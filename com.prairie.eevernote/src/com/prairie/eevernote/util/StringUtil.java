package com.prairie.eevernote.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.Platform;

/**
 * The StringUtil class defines String related utility.
 *
 * @author Liu, Jianwei
 *
 */
public final class StringUtil implements ConstantsUtil {

    public static final String CRLF = org.apache.commons.lang3.StringUtils.CR + org.apache.commons.lang3.StringUtils.LF;

    /**
     * <p>
     * Checks if a String is null. This has same effect to
     * {@code string == null}.
     * </p>
     *
     * <pre>
     * StringUtil.isNull(null)      = true
     * StringUtil.isNull("")        = true
     * StringUtil.isNull(" ")       = false
     * StringUtil.isNull("abc")     = false
     * </pre>
     *
     * @param string
     *            the String to check
     * @return {@code true} if the String is null
     */
    public static boolean isNull(final String string) {
        return string == null;
    }

    /**
     * <p>
     * Compares two strings given. The result is true if the two strings are
     * both null, empty or blank, or two strings that represent the same
     * sequence of characters, i.e. {@code str1.equals(str2)}.
     * </p>
     *
     * <pre>
     * StringUtil.equalsInLogic(null, null)   = true
     * StringUtil.equalsInLogic(null, "")  = true
     * StringUtil.equalsInLogic(null, " ")  = true
     * StringUtil.equalsInLogic("abc", "abc") = true
     * StringUtil.equalsIgnoreCase("abc", "ABC") = false
     * StringUtil.equalsIgnoreCase("abc", "abd") = false
     * </pre>
     *
     * @param str1
     *            the first CharSequence, may be null
     * @param str2
     *            the second CharSequence, may be null
     * @return true if the given two strings represent a logic equivalent, false
     *         otherwise
     */
    public static boolean equalsInLogic(final String str1, final String str2) {
        return StringUtils.isBlank(str1) ? StringUtils.isBlank(str2) : str1.equals(str2);
    }

    /**
     * <p>
     * Escapes the characters in a String using ENML entities.
     * </p>
     *
     * @param string
     *            the String to escape, may be null
     * @return a new escaped String, null if null string input
     */
    public static String escapeEnml(final String string) {
        if (isNull(string)) {
            return string;
        }
        String escapedXml = StringEscapeUtils.escapeXml10(string);

        escapedXml = escapedXml.replaceAll(StringUtils.SPACE, HTML_NBSP);

        int tabWidth = Platform.getPreferencesService().getInt(PLUGIN_ORG_ECLIPSE_JDT_CORE_NAME, PLUGIN_ORG_ECLIPSE_JDT_CORE_PREF_FORMATTER_TABULATION_SIZE, ZERO, null);
        escapedXml = escapedXml.replaceAll(TAB, org.apache.commons.lang3.StringUtils.repeat(HTML_NBSP, tabWidth));

        return escapedXml;
    }

    /**
     * <p>
     * Search the source string for the first substring that matched regular
     * expression.
     * </p>
     *
     * @param source
     *            The character sequence to be searched
     * @param regex
     *            The regular expression to be searched for
     *
     * @return the end offset of matched subsequence, -1 if no subsequence of
     *         the input sequence matches this matcher's pattern
     */
    public static int find(final String source, final String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(source);

        boolean find = matcher.find();

        return find ? matcher.end() : NEGATIVE;
    }

    public static String[] splitByMultipleSeparatorsPreserveAllTokens(final String str, final String[] separators) {
        String[] transition = ArrayUtils.EMPTY_STRING_ARRAY;
        String[] splits = { str };
        for (String sep : separators) {
            for (String part : splits) {
                String[] r = StringUtils.isEmpty(part) ? ArrayUtils.toArray(part) : StringUtils.splitByWholeSeparatorPreserveAllTokens(part, sep);
                transition = ArrayUtils.addAll(transition, r);
            }
            splits = transition;
            transition = ArrayUtils.EMPTY_STRING_ARRAY;
        }
        return splits;
    }

    public static int indexOfAny(final String selectionText, final String[] array, final int startPosition) {
        String subStrFromStart = selectionText.substring(startPosition);
        int i = StringUtils.indexOfAny(subStrFromStart, array);
        return i < 0 ? i : startPosition + i;
    }

    /**
     * <pre>
     * StringUtil.toQuotedString("abc") = ""abc"";
     * </pre>
     * 
     * @param str
     *            string to quote, may be null.
     * @return quoted string
     */
    public static String toQuotedString(final String str) {
        return DOUBLE_QUOTATION + str + DOUBLE_QUOTATION;
    }
}
