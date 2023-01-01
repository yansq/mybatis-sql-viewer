package io.github.linyimin.plugin.ui.tree;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.psi.PsiElement;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.PsiNavigateUtil;
import io.github.linyimin.plugin.ProcessResult;
import io.github.linyimin.plugin.configuration.model.MybatisSqlConfiguration;
import io.github.linyimin.plugin.constant.Constant;
import io.github.linyimin.plugin.sql.checker.Checker;
import io.github.linyimin.plugin.sql.checker.CheckerHolder;
import io.github.linyimin.plugin.sql.checker.Report;
import io.github.linyimin.plugin.sql.checker.enums.CheckScopeEnum;
import io.github.linyimin.plugin.sql.converter.ResultConverter;
import io.github.linyimin.plugin.sql.executor.SqlExecutor;
import io.github.linyimin.plugin.sql.parser.SqlParser;
import io.github.linyimin.plugin.sql.result.SelectResult;
import io.github.linyimin.plugin.ui.MouseCursorAdapter;
import io.github.linyimin.plugin.ui.MybatisSqlScannerPanel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * @author banzhe
 * @date 2023/01/02 01:03
 **/
public class TreeListener extends MouseAdapter {

    private PsiElement psiElement;
    private final MybatisSqlScannerPanel mybatisSqlScannerPanel;

    private final BackgroundTaskQueue backgroundTaskQueue;

    public TreeListener(MybatisSqlScannerPanel panel) {
        this.mybatisSqlScannerPanel = panel;

        this.backgroundTaskQueue = new BackgroundTaskQueue(panel.getProject(), Constant.APPLICATION_NAME);

        JButton jumpButton = panel.getJumpButton();

        jumpButton.addMouseListener(new MouseCursorAdapter(jumpButton));
        jumpButton.addActionListener(e -> {
            PsiNavigateUtil.navigate(psiElement);
        });

    }

    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        if (!(e.getSource() instanceof SimpleTree)) {
            return;
        }
        SimpleTree selectedTree = (SimpleTree) e.getSource();
        SimpleNode selectedNode = selectedTree.getSelectedNode();

        if (selectedNode instanceof MethodTreeNode) {

            backgroundTaskQueue.run(new Task.Backgroundable(this.mybatisSqlScannerPanel.getProject(), Constant.APPLICATION_NAME) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    doAction((MethodTreeNode) selectedNode);
                }
            });
        }
    }

    private void doAction(MethodTreeNode selectedNode) {
        MybatisSqlConfiguration configuration = selectedNode.getConfiguration();
        this.psiElement = configuration.getPsiElement();
        this.updateSqlPanel(configuration);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);
    }

    public void updateSqlPanel(MybatisSqlConfiguration configuration) {
        this.updateSql(configuration.getSql());
        this.validateSql(configuration.getSql());
        this.explainSql(configuration.getSql());
    }

    private void updateSql(String sql) {
        ApplicationManager.getApplication().invokeLater(() -> this.mybatisSqlScannerPanel.getStatementText().setText(sql));
    }

    private void validateSql(String sql) {
        try {
            ProcessResult<String> validateResult = SqlParser.validate(sql);

            if (!validateResult.isSuccess()) {
                ApplicationManager.getApplication().invokeLater(() -> this.mybatisSqlScannerPanel.getStatementRuleText().setText(validateResult.getErrorMsg()));
            } else {

                CheckScopeEnum scope = SqlParser.getCheckScope(sql);
                Checker checker = CheckerHolder.getChecker(scope);

                if (checker == null) {
                    ApplicationManager.getApplication().invokeLater(() -> this.mybatisSqlScannerPanel.getStatementRuleText().setText("No checker for the statement."));
                    return;
                }

                List<Report> reports = checker.check(sql);
                String ruleInfo = ResultConverter.convert2RuleInfo(scope, reports);

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (StringUtils.isBlank(ruleInfo)) {
                        this.mybatisSqlScannerPanel.getStatementRuleText().setText("满足规范要求");
                    } else {
                        this.mybatisSqlScannerPanel.getStatementRuleText().setText(ruleInfo);
                    }
                });
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            this.mybatisSqlScannerPanel.getStatementRuleText().setText(String.format("Validate sql statement error.\n%s", sw));
        }
    }

    private void explainSql(String sql) {

        ApplicationManager.getApplication().invokeLater(() -> {
            mybatisSqlScannerPanel.getIndexPanel().setLayout(new BorderLayout());
            mybatisSqlScannerPanel.getIndexPanel().remove(mybatisSqlScannerPanel.getIndexScrollPane());
            mybatisSqlScannerPanel.getIndexPanel().remove(mybatisSqlScannerPanel.getInfoPane().getInfoPane());
            mybatisSqlScannerPanel.getIndexPanel().add(mybatisSqlScannerPanel.getInfoPane().getInfoPane());
            mybatisSqlScannerPanel.getInfoPane().setText("Loading explain result...");
        });

        try {
            String explainSql = String.format("explain %s", sql);
            SelectResult executeResult = (SelectResult) SqlExecutor.executeSql(this.mybatisSqlScannerPanel.getProject(), explainSql, false);

            ApplicationManager.getApplication().invokeLater(() -> {
                mybatisSqlScannerPanel.getIndexPanel().setLayout(new BorderLayout());
                mybatisSqlScannerPanel.getIndexPanel().remove(mybatisSqlScannerPanel.getIndexScrollPane());
                mybatisSqlScannerPanel.getIndexPanel().remove(mybatisSqlScannerPanel.getInfoPane().getInfoPane());
                mybatisSqlScannerPanel.getIndexPanel().add(mybatisSqlScannerPanel.getIndexScrollPane());
                this.mybatisSqlScannerPanel.getIndexTable().setModel(executeResult.getModel());
            });
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String prompt = String.format("Execute Explain Sql Failed.\n%s", sw);
            ApplicationManager.getApplication().invokeLater(() -> mybatisSqlScannerPanel.getInfoPane().setText(prompt));
        }
    }
}
