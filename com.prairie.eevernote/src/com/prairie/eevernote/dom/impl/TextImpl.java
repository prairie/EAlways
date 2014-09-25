package com.prairie.eevernote.dom.impl;

import org.apache.commons.lang3.StringUtils;

import com.prairie.eevernote.Constants;
import com.prairie.eevernote.Messages;
import com.prairie.eevernote.dom.DOMException;
import com.prairie.eevernote.dom.Node;
import com.prairie.eevernote.dom.Text;

public class TextImpl extends NodeImpl implements Text, Constants {

    private String text;

    protected TextImpl(final String text) {
        this.text = text;
    }

    @Override
    public int getLength() {
        return text.length();
    }

    @Override
    public void appendText(final String text) {
        this.text.concat(text);
    }

    @Override
    public void insertText(final int offset, final String text) throws DOMException {
        if (offset < 0 || offset >= this.text.length()) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, Messages.getString(DOM_ERROR9));
        }
        this.text = StringUtils.join(this.text.substring(0, offset), text, this.text.substring(offset));
    }

    @Override
    public void deleteText(final int offset, final int count) throws DOMException {
        if (offset < 0 || offset >= text.length() || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, Messages.getString(DOM_ERROR8));
        }
        text = StringUtils.join(text.substring(0, offset), text.substring(offset + count));
    }

    @Override
    public void replaceText(final int offset, final int count, final String text) throws DOMException {
        if (offset < 0 || offset >= this.text.length() || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, Messages.getString(DOM_ERROR8));
        }
        this.text = StringUtils.join(this.text.substring(0, offset), text, this.text.substring(offset + count));
    }

    @Override
    public String getNodeName() {
        return DOM_NODE_NAME_TEXT;
    }

    @Override
    public String getNodeValue() {
        return text;
    }

    @Override
    public void setNodeValue(final String value) {
        text = value;
    }

    @Override
    public short getNodeType() {
        return Node.TEXT_NODE;
    }

    @Override
    public String getTextContent() {
        return text;
    }

    @Override
    public void setTextContent(final String text) {
        setNodeValue(text);
    }

    @Override
    public boolean isContentEmpty() {
        return StringUtils.isEmpty(text);
    }

    @Override
    public String toString() {
        return text;
    }

}
