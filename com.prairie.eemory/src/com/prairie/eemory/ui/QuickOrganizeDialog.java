package com.prairie.eemory.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.prairie.eemory.Constants;
import com.prairie.eemory.Messages;
import com.prairie.eemory.client.EDAMLimits;
import com.prairie.eemory.client.EeClipper;
import com.prairie.eemory.client.EeClipperFactory;
import com.prairie.eemory.client.ENNote;
import com.prairie.eemory.client.impl.ENNoteImpl;
import com.prairie.eemory.exception.ThrowableHandler;
import com.prairie.eemory.util.ConstantsUtil;
import com.prairie.eemory.util.EclipseUtil;
import com.prairie.eemory.util.EncryptionUtil;
import com.prairie.eemory.util.IDialogSettingsUtil;
import com.prairie.eemory.util.ListUtil;
import com.prairie.eemory.util.MapUtil;
import com.prairie.eemory.util.StringUtil;

public class QuickOrganizeDialog extends Dialog implements Constants {

    public static final int SHOULD_NOT_SHOW = -1;

    private final Shell shell;
    private static QuickOrganizeDialog thisDialog;

    private EeClipper clipper;

    private Map<String, String> notebooks; // <Name, Guid>
    private Map<String, ENNote> notes; // <Name, Guid>
    private List<String> tags;

    private SimpleContentProposalProvider noteProposalProvider;

    private Map<String, Text> fields;
    private ENNote quickSettings;
    // <Field Property, <Field Property, Field Value>>
    private Map<String, Map<String, String>> matrix;

    private boolean canceled = false;

    public QuickOrganizeDialog(final Shell parentShell) {
        super(parentShell);
        shell = parentShell;
        notebooks = MapUtil.map();
        notes = MapUtil.map();
        tags = ListUtil.list();
        clipper = EeClipperFactory.getInstance().getEEClipper();
    }

    @Override
    protected void configureShell(final Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Messages.Plugin_Configs_QuickOrgnize_Shell_Title);
    }

    @Override
    protected void setShellStyle(final int newShellStyle) {
        super.setShellStyle(newShellStyle | SWT.RESIZE);
    }

    @Override
    protected Control createDialogArea(final Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        // container.setLayoutData(new GridData(GridData.FILL_BOTH));
        container.setLayout(new GridLayout(2, false));
        container.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

        // ------------

        authInProgress();

        // ------------

        if (shouldShow(PLUGIN_SETTINGS_SECTION_NOTEBOOK, PLUGIN_SETTINGS_KEY_GUID)) {
            Text notebookField = createLabelTextField(container, Messages.Plugin_Configs_Notebook);
            notebookField.setTextLimit(EDAMLimits.EDAM_NOTEBOOK_NAME_LEN_MAX);
            addField(PLUGIN_CONFIGS_NOTEBOOK, notebookField);
            fetchNotebooksInProgres();
            EclipseUtil.enableFilteringContentAssist(notebookField, notebooks.keySet().toArray(new String[notebooks.size()]));
        }

        // ------------

        if (shouldShow(PLUGIN_SETTINGS_SECTION_NOTE, PLUGIN_SETTINGS_KEY_GUID)) {
            Text noteField = createLabelTextField(container, Messages.Plugin_Configs_Note);
            noteField.setTextLimit(EDAMLimits.EDAM_NOTE_TITLE_LEN_MAX);
            addField(PLUGIN_CONFIGS_NOTE, noteField);
            fetchNotesInProgres();
            noteProposalProvider = EclipseUtil.enableFilteringContentAssist(noteField, notes.keySet().toArray(new String[notes.size()]));
            if (shouldShow(PLUGIN_SETTINGS_SECTION_NOTEBOOK, PLUGIN_SETTINGS_KEY_GUID)) {
                noteField.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(final FocusEvent e) {
                        if (shouldRefresh(PLUGIN_CONFIGS_NOTE, PLUGIN_CONFIGS_NOTEBOOK)) {
                            final String hotebook = getFieldValue(PLUGIN_CONFIGS_NOTEBOOK);
                            BusyIndicator.showWhile(Display.getDefault(), new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        notes = clipper.listNotesWithinNotebook(ENNoteImpl.forNotebookGuid(notebooks.get(hotebook)));
                                    } catch (Throwable e) {
                                        boolean fixed = ThrowableHandler.handleDesignTimeErr(shell, e, clipper);
                                        if (fixed) {
                                            try {
                                                notes = clipper.listNotesWithinNotebook(ENNoteImpl.forNotebookGuid(notebooks.get(hotebook)));
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    }
                                }
                            });
                            String[] ns = notes.keySet().toArray(new String[notes.size()]);
                            Arrays.sort(ns);
                            noteProposalProvider.setProposals(ns);
                        }
                    }
                });
            }
        }

        // ------------

        if (shouldShow(PLUGIN_SETTINGS_SECTION_TAGS, PLUGIN_SETTINGS_KEY_NAME)) {
            Text tagsField = createLabelTextField(container, Messages.Plugin_Configs_Tags);
            tagsField.setTextLimit(EDAMLimits.EDAM_TAG_NAME_LEN_MAX);
            addField(PLUGIN_CONFIGS_TAGS, tagsField);
            fetchTagsInProgress();
            EclipseUtil.enableFilteringContentAssist(tagsField, tags.toArray(new String[tags.size()]), TAGS_SEPARATOR);
        }

        // ------------

        if (shouldShow(PLUGIN_SETTINGS_SECTION_COMMENTS, PLUGIN_SETTINGS_KEY_NAME)) {
            addField(PLUGIN_CONFIGS_COMMENTS, createLabelTextField(container, Messages.Plugin_Configs_Comments));
        }

        return container;
    }

    private void authInProgress() {
        if (isCanceled()) {
            return;
        }
        try {
            new ProgressMonitorDialog(shell).run(true, true, new IRunnableWithProgress() {
                @Override
                public void run(final IProgressMonitor monitor) {
                    monitor.beginTask(Messages.Plugin_Configs_Authenticating, 1);
                    try {
                        clipper = EeClipperFactory.getInstance().getEEClipper(EncryptionUtil.decrypt(IDialogSettingsUtil.get(PLUGIN_SETTINGS_KEY_TOKEN)), false);
                    } catch (Throwable e) {
                        boolean fixed = ThrowableHandler.handleDesignTimeErr(shell, e, true, clipper);
                        if (fixed) {
                            try {
                                clipper = EeClipperFactory.getInstance().getEEClipper(EncryptionUtil.decrypt(IDialogSettingsUtil.get(PLUGIN_SETTINGS_KEY_TOKEN)), false);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    setCanceled(monitor.isCanceled());
                    monitor.done();
                }
            });
        } catch (Throwable e) {
            ThrowableHandler.handleDesignTimeErr(shell, e, true);
        }
    }

    private void fetchNotebooksInProgres() {
        if (isCanceled()) {
            return;
        }
        try {
            new ProgressMonitorDialog(shell).run(true, true, new IRunnableWithProgress() {
                @Override
                public void run(final IProgressMonitor monitor) {
                    monitor.beginTask(Messages.Plugin_Configs_FetchingNotebooks, 1);
                    try {
                        notebooks = clipper.listNotebooks();
                    } catch (Throwable e) {
                        boolean fixed = ThrowableHandler.handleDesignTimeErr(shell, e, clipper);
                        if (fixed) {
                            try {
                                notebooks = clipper.listNotebooks();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    setCanceled(monitor.isCanceled());
                    monitor.done();
                }
            });
        } catch (Throwable e) {
            ThrowableHandler.handleDesignTimeErr(shell, e);
        }
    }

    private void fetchNotesInProgres() {
        if (isCanceled()) {
            return;
        }
        try {
            new ProgressMonitorDialog(shell).run(true, true, new IRunnableWithProgress() {
                @Override
                public void run(final IProgressMonitor monitor) {
                    monitor.beginTask(Messages.Plugin_Configs_FetchingNotes, 1);
                    try {
                        notes = clipper.listNotesWithinNotebook(ENNoteImpl.forNotebookGuid(IDialogSettingsUtil.get(PLUGIN_SETTINGS_SECTION_NOTEBOOK, PLUGIN_SETTINGS_KEY_GUID)));
                    } catch (Throwable e) {
                        boolean fixed = ThrowableHandler.handleDesignTimeErr(shell, e, clipper);
                        if (fixed) {
                            try {
                                notes = clipper.listNotesWithinNotebook(ENNoteImpl.forNotebookGuid(IDialogSettingsUtil.get(PLUGIN_SETTINGS_SECTION_NOTEBOOK, PLUGIN_SETTINGS_KEY_GUID)));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    setCanceled(monitor.isCanceled());
                    monitor.done();
                }
            });
        } catch (Throwable e) {
            ThrowableHandler.handleDesignTimeErr(shell, e);
        }
    }

    private void fetchTagsInProgress() {
        if (isCanceled()) {
            return;
        }
        try {
            new ProgressMonitorDialog(shell).run(true, true, new IRunnableWithProgress() {
                @Override
                public void run(final IProgressMonitor monitor) {
                    monitor.beginTask(Messages.Plugin_Configs_FetchingTags, 1);
                    try {
                        tags = clipper.listTags();
                    } catch (Throwable e) {
                        boolean fixed = ThrowableHandler.handleDesignTimeErr(shell, e, clipper);
                        if (fixed) {
                            try {
                                tags = clipper.listTags();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    setCanceled(monitor.isCanceled());
                    monitor.done();
                }
            });
        } catch (Throwable e) {
            ThrowableHandler.handleDesignTimeErr(shell, e);
        }
    }

    @Override
    protected void createButtonsForButtonBar(final Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(450, 200);
    }

    @Override
    protected void okPressed() {
        saveQuickSettings();
        if (!confirmDefault()) {
            return;
        }
        super.okPressed();
    }

    private boolean confirmDefault() {
        boolean confirm = false;
        String msg = StringUtils.EMPTY;
        if (shouldShow(PLUGIN_SETTINGS_SECTION_NOTE, PLUGIN_SETTINGS_KEY_GUID) && StringUtils.isBlank(quickSettings.getGuid())) {
            msg = StringUtils.isBlank(quickSettings.getName()) ? Messages.Plugin_Runtime_CreateNewNote : Messages.bind(Messages.Plugin_Runtime_CreateNewNoteWithGivenName, quickSettings.getName());
            confirm = true;
        } else if (shouldShow(PLUGIN_SETTINGS_SECTION_NOTEBOOK, PLUGIN_SETTINGS_KEY_GUID) && StringUtils.isBlank(quickSettings.getNotebook().getGuid())) {
            msg = Messages.Plugin_Runtime_ClipToDefault;
            confirm = true;
        }
        return confirm ? MessageDialog.openQuestion(shell, Messages.Plugin_Configs_QuickOrgnize_Shell_Title, msg) : true;
    }

    private void saveQuickSettings() {
        quickSettings = new ENNoteImpl();

        quickSettings.getNotebook().setName(getFieldValue(PLUGIN_CONFIGS_NOTEBOOK));
        quickSettings.getNotebook().setGuid(notebooks.get(getFieldValue(PLUGIN_CONFIGS_NOTEBOOK)));

        ENNote note = notes.get(getFieldValue(PLUGIN_CONFIGS_NOTE));
        quickSettings.setName(note != null ? note.getName() : getFieldValue(PLUGIN_CONFIGS_NOTE));
        quickSettings.setGuid(note != null ? note.getGuid() : null);

        String tags = getFieldValue(PLUGIN_CONFIGS_TAGS);
        if (StringUtils.isNotBlank(tags)) {
            quickSettings.setTags(ListUtil.toList(tags.split(TAGS_SEPARATOR)));
        }

        quickSettings.setComments(getFieldValue(PLUGIN_CONFIGS_COMMENTS));
    }

    public ENNote getQuickSettings() {
        return quickSettings;
    }

    private boolean shouldRefresh(final String uniqueKey, final String property) {
        return fieldValueChanged(uniqueKey, property);
    }

    private boolean fieldValueChanged(final String uniqueKey, final String property) {
        if (matrix == null) {
            matrix = MapUtil.map();
        }
        Map<String, String> map = matrix.get(uniqueKey);
        if (map == null) {
            map = MapUtil.map();
            matrix.put(uniqueKey, map);
        }
        if (!StringUtil.equalsInLogic(getFieldValue(property), map.get(property))) {
            map.put(property, getFieldValue(property));
            return true;
        }
        return false;
    }

    public static int show(final Shell shell) {
        if (shouldShow()) {
            thisDialog = new QuickOrganizeDialog(shell);
            return thisDialog.open();
        }
        return QuickOrganizeDialog.SHOULD_NOT_SHOW;
    }

    protected static boolean shouldShow() {
        return shouldShow(PLUGIN_SETTINGS_SECTION_NOTEBOOK, PLUGIN_SETTINGS_KEY_GUID) || shouldShow(PLUGIN_SETTINGS_SECTION_NOTE, PLUGIN_SETTINGS_KEY_GUID) || shouldShow(PLUGIN_SETTINGS_SECTION_TAGS, PLUGIN_SETTINGS_KEY_NAME) || shouldShow(PLUGIN_SETTINGS_SECTION_COMMENTS, PLUGIN_SETTINGS_KEY_NAME);
    }

    private static boolean shouldShow(final String property, final String key) {
        if (property.equals(PLUGIN_SETTINGS_SECTION_NOTEBOOK)) {
            if (IDialogSettingsUtil.getBoolean(PLUGIN_SETTINGS_SECTION_NOTE, PLUGIN_SETTINGS_KEY_CHECKED) && !shouldShow(PLUGIN_SETTINGS_SECTION_NOTE, key)) {
                return false;
            }
        }
        boolean checked = IDialogSettingsUtil.getBoolean(property, PLUGIN_SETTINGS_KEY_CHECKED);
        String value = IDialogSettingsUtil.get(property, key);
        return checked && StringUtils.isBlank(value);
    }

    protected Text createLabelTextField(final Composite container, final String labelText) {
        Label label = new Label(container, SWT.NONE);
        label.setText(labelText + ConstantsUtil.COLON);

        Text text = new Text(container, SWT.BORDER);
        text.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

        return text;
    }

    protected String getFieldValue(final String property) {
        Text text = (Text) getField(property);
        if (text == null) {
            return null;
        }
        return text.getText().trim();
    }

    protected Control getField(final String property) {
        if (fields == null) {
            return null;
        }
        return fields.get(property);
    }

    protected void addField(final String key, final Text value) {
        if (fields == null) {
            fields = MapUtil.map();
        }
        fields.put(key, value);
    }

    public static QuickOrganizeDialog getThis() {
        return thisDialog;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(final boolean canceled) {
        this.canceled = canceled;
    }

}