package io.github.linyimin.plugin.ui;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;

/**
 * @author yansq
 * @version V1.0
 * @package io.github.linyimin.plugin.ui
 * @date 2025/1/15 15:36
 */
public class IntegerInputVerifier extends DocumentFilter {
    @Override
    public void insertString(DocumentFilter.FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
        if (string == null || string.isEmpty() || string.matches("\\d+")) {
            super.insertString(fb, offset, string, attr);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String string, AttributeSet attrs) throws BadLocationException {
        if (string == null || string.isEmpty() || string.matches("\\d+")) {
            super.replace(fb, offset, length, string, attrs);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}