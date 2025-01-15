package io.github.linyimin.plugin.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.TitledSeparator;
import io.github.linyimin.plugin.settings.SqlViewerSettingsState;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;

/**
 * @author yiminlin
 * @date 2023/04/16 14:30
 **/
public class SqlViewerSettingsPanel implements Configurable, Disposable {
    private JPanel functionalityTitledBorderPanel;
    private JCheckBox fileJumpEnableBox;
    private JPanel myMainPanel;
    private JFormattedTextField maxRowsReturnedField;

    private boolean needRestart = false;

    public SqlViewerSettingsPanel() {
        init();
    }

    private void init() {
        assert maxRowsReturnedField != null;
        ((AbstractDocument)maxRowsReturnedField.getDocument()).setDocumentFilter(new IntegerInputVerifier());
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Mybatis Sql Viewer";
    }

    @Override
    public @Nullable JComponent createComponent() {
        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        SqlViewerSettingsState state = SqlViewerSettingsState.getInstance();
        if (state.fileJumpEnable != fileJumpEnableBox.isSelected()) {
            needRestart = true;
            return true;
        }
        String text = maxRowsReturnedField.getText();
        return !text.isEmpty() && !text.equals(String.valueOf(state.maxRowsReturnedField));
    }

    @Override
    public void reset() {
        SqlViewerSettingsState state = SqlViewerSettingsState.getInstance();
        fileJumpEnableBox.setSelected(state.fileJumpEnable);
        maxRowsReturnedField.setText(String.valueOf(state.maxRowsReturnedField));
    }

    @Override
    public void apply() throws ConfigurationException {

        SqlViewerSettingsState state = SqlViewerSettingsState.getInstance();

        state.fileJumpEnable = fileJumpEnableBox.isSelected();
        state.maxRowsReturnedField = Integer.parseInt(maxRowsReturnedField.getText());

        if (!needRestart) {
            return;
        }

        int yesNo = MessageDialogBuilder.yesNo("Settings changed!", "Requires restarting the IDE to take effect. " +
                        "Do you want to restart to apply the settings?")
                .yesText("Restart")
                .noText("Not Now").show();

        if (yesNo == Messages.YES) {
            ApplicationManagerEx.getApplicationEx().restart(true);
        }

    }

    @Override
    public void dispose() {

    }

    private void createUIComponents() {
        functionalityTitledBorderPanel = new JPanel(new BorderLayout());
        TitledSeparator functionalitySeparator = new TitledSeparator("Functionality Settings");
        functionalityTitledBorderPanel.add(functionalitySeparator, BorderLayout.CENTER);
    }
}
