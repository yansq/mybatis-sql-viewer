package io.github.linyimin.plugin.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import io.github.linyimin.plugin.ProcessResult;
import io.github.linyimin.plugin.component.SqlParamGenerateComponent;
import io.github.linyimin.plugin.configuration.GlobalConfig;
import io.github.linyimin.plugin.configuration.MybatisSqlStateComponent;
import io.github.linyimin.plugin.configuration.model.MybatisSqlConfiguration;
import io.github.linyimin.plugin.sql.checker.Checker;
import io.github.linyimin.plugin.sql.checker.CheckerHolder;
import io.github.linyimin.plugin.sql.checker.Report;
import io.github.linyimin.plugin.sql.checker.enums.CheckScopeEnum;
import io.github.linyimin.plugin.sql.converter.ResultConverter;
import io.github.linyimin.plugin.sql.executor.SqlExecutor;
import io.github.linyimin.plugin.sql.parser.SqlParser;
import io.github.linyimin.plugin.sql.parser.SqlType;
import io.github.linyimin.plugin.sql.result.BaseResult;
import io.github.linyimin.plugin.sql.result.SelectResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static io.github.linyimin.plugin.constant.Constant.*;

/**
 * @author banzhe
 * @date 2022/12/03 17:27
 **/
public class SqlTabbedPane implements TabbedChangeListener {

    private JPanel panel1;

    private JTabbedPane sqlTabbedPanel;

    private JPanel statementTabbedPane;
    private JPanel statementContentPane;

    private JPanel resultTabbedPane;
    private JPanel resultContentPane;

    // sql语句
    private JPanel statementPanel;
    private RSyntaxTextArea statementText;
    private RTextScrollPane statementScroll;

    // sql规约
    private JPanel statementRulePanel;
    private RSyntaxTextArea statementRuleText;

    // sql执行信息
    private RSyntaxTextArea executeInfoText;
    private JPanel executeInfoPanel;

    // sql执行计划
    private JTable executeHitIndexTable;
    private JScrollPane executeHitIndexScroll;

    // sql执行结果
    private JTable executeResultTable;
    private JScrollPane executeResultScroll;

    // sql压测面板
    private JPanel stressPane;

    private final SqlStressTabbedPane sqlStressTabbedPane;

    private final InfoPane infoPane;
    private final Project project;
    private final BackgroundTaskQueue backgroundTaskQueue;

    public SqlTabbedPane(Project project) {

        this.project = project;
        this.backgroundTaskQueue = new BackgroundTaskQueue(project, APPLICATION_NAME);

        this.sqlStressTabbedPane = new SqlStressTabbedPane(this.project);

        this.infoPane = new InfoPane();

        initSqlPanel();
        initResultPanel();
        initSqlStressPanel();

        setTableRowHeight();
        setScrollUnitIncrement();
        // 监听sql tabbed panel的点击事件
        this.sqlTabbedPanel.addChangeListener(e -> sqlTabbedPanelListener(false));

        this.sqlTabbedPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!GlobalConfig.isMybatisMode || event.getClickCount() != 2 || sqlTabbedPanel.indexAtLocation(event.getX(), event.getY()) < 0) {
                    return;
                }
                sqlTabbedPanelListener(true);
            }
        });

        this.sqlTabbedPanel.addMouseListener(new MouseCursorAdapter(this.sqlTabbedPanel));
    }

    private void initSqlStressPanel() {
        this.stressPane.setLayout(new BorderLayout());
        this.stressPane.add(this.sqlStressTabbedPane.getSqlStressPane());
    }

    private void createUIComponents() {
        this.executeResultTable = new JTable() {
            //Implement table cell tool tips.
            public String getToolTipText(@NotNull MouseEvent e) {
                String tip = null;
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);

                try {
                    //comment row, exclude heading
                    tip = getValueAt(rowIndex, colIndex).toString();
                } catch (RuntimeException e1) {
                    //catch null pointer exception if mouse is over an empty line
                }
                return tip;
            }
        };
    }

    private void initResultPanel() {

        executeInfoText = CustomTextField.createArea("json");
        executeInfoText.setRows(3);
        executeInfoText.setEditable(false);

        RTextScrollPane executeInfoTextScroll = new RTextScrollPane(executeInfoText);
        executeInfoTextScroll.setBorder(LINE_BORDER);

        executeInfoPanel.setLayout(new BorderLayout());
        executeInfoPanel.add(executeInfoTextScroll);

        executeHitIndexScroll.setBorder(LINE_BORDER);
        executeResultScroll.setBorder(LINE_BORDER);

    }

    private void initSqlPanel() {

        statementText = CustomTextField.createArea("sql");
        statementText.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (StringUtils.equals(statementText.getText(), SQL_STATEMENT_LOADING_PROMPT)) {
                    return;
                }
                MybatisSqlConfiguration sqlConfig = project.getService(MybatisSqlStateComponent.class).getConfiguration();
                sqlConfig.setSql(statementText.getText());
                validateSql(sqlConfig.getSql());
            }
        });

        statementPanel.setLayout(new BorderLayout());

        statementScroll = new RTextScrollPane(statementText);
        statementScroll.setBorder(new EmptyBorder(JBUI.emptyInsets()));

        statementPanel.add(statementScroll);

        statementRuleText = CustomTextField.createArea("sql");
        statementRuleText.setEditable(false);

        statementRulePanel.setLayout(new BorderLayout());

        RTextScrollPane statementRuleScroll = new RTextScrollPane(statementRuleText);
        statementRuleScroll.setBorder(new EmptyBorder(JBUI.emptyInsets()));

        statementRulePanel.add(statementRuleScroll);

        this.statementTabbedPane.setLayout(new BorderLayout());
        this.statementTabbedPane.remove(statementContentPane);
        this.statementTabbedPane.add(this.infoPane.getInfoPane());

        this.resultTabbedPane.setLayout(new BorderLayout());
        this.resultTabbedPane.remove(resultContentPane);
        this.resultTabbedPane.add(this.infoPane.getInfoPane());

    }

    private void setTableRowHeight() {
        this.executeResultTable.setRowHeight(TABLE_ROW_HEIGHT);
        this.executeHitIndexTable.setRowHeight(TABLE_ROW_HEIGHT);
    }

    private void setScrollUnitIncrement() {
        int unit = 16;
        this.statementScroll.getVerticalScrollBar().setUnitIncrement(unit);
        this.statementScroll.getHorizontalScrollBar().setUnitIncrement(unit);
    }

    private void sqlTabbedPanelListener(boolean forceUpdate) {

        int selectedIndex = sqlTabbedPanel.getSelectedIndex();

        // 完整SQL语句
        if (selectedIndex == SqlComponentType.statement.index) {
            updateSql(forceUpdate);
        }

        // 执行SQL
        if (selectedIndex == SqlComponentType.result.getIndex()) {
            if (forceUpdate) {
                updateSql(true);
            }
            executeSql();
        }

        // sql压测
        if (selectedIndex == SqlComponentType.stress.getIndex()) {
            if (forceUpdate) {
                updateSql(true);
            }
            this.sqlStressTabbedPane.updateStressConfig();
        }
    }

    private void updateSql(boolean forceUpdate) {
        if (GlobalConfig.isMybatisMode) {
            backgroundTaskQueue.run(new Task.Backgroundable(project, APPLICATION_NAME) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    MybatisSqlConfiguration sqlConfig = project.getService(MybatisSqlStateComponent.class).getConfiguration();

                    if (forceUpdate || sqlConfig.isUpdateSql()) {
                        ProcessResult<String> result = updateSqlBackground();
                        if (!result.isSuccess()) {
                            ApplicationManager.getApplication().invokeLater(() -> infoPane.setText(result.getErrorMsg()));
                            return;
                        }
                    }

                    ApplicationManager.getApplication().invokeLater(() -> {
                        SqlTabbedPane.this.statementTabbedPane.remove(statementContentPane);
                        SqlTabbedPane.this.statementTabbedPane.remove(infoPane.getInfoPane());

                        SqlTabbedPane.this.statementTabbedPane.add(statementContentPane);

                        statementText.setText(sqlConfig.getSql());
                    });
                    validateSql(sqlConfig.getSql());
                }
            });
        } else {
            backgroundTaskQueue.run(new Task.Backgroundable(project, APPLICATION_NAME) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        SqlTabbedPane.this.statementTabbedPane.remove(statementContentPane);
                        SqlTabbedPane.this.statementTabbedPane.remove(infoPane.getInfoPane());
                        SqlTabbedPane.this.statementTabbedPane.add(statementContentPane);
                    });
                    validateSql(statementText.getText());
                }
            });
        }
    }

    private ProcessResult<String> updateSqlBackground() {

        ApplicationManager.getApplication().invokeLater(() -> {

            this.statementTabbedPane.remove(this.infoPane.getInfoPane());
            this.statementTabbedPane.remove(statementContentPane);

            this.statementTabbedPane.add(this.infoPane.getInfoPane());

            infoPane.setText(SQL_STATEMENT_LOADING_PROMPT);
        });

        return SqlParamGenerateComponent.generateSql(project);
    }

    private void validateSql(String sql) {
        try {
            ProcessResult<String> validateResult = SqlParser.validate(sql);

            if (!validateResult.isSuccess()) {
                statementRuleText.setText(validateResult.getErrorMsg());
            } else {

                List<Report> reports = checkRules(sql);
                if (CollectionUtils.isEmpty(reports)) {
                    ApplicationManager.getApplication().invokeLater(() -> statementRuleText.setText("No rule checker for the statement."));
                    return;
                }

                String ruleInfo = ResultConverter.convert2RuleInfo(SqlParser.getCheckScope(sql), reports);

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (StringUtils.isBlank(ruleInfo)) {
                        statementRuleText.setText("满足规范要求");
                    } else {
                        statementRuleText.setText(ruleInfo);
                    }
                });
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            statementRuleText.setText(String.format("Validate sql statement error.\n%s", sw));
        }
    }

    private List<Report> checkRules(String sql) {

        List<Checker> checkers = CheckerHolder.getCheckers(SqlParser.getCheckScope(sql), CheckScopeEnum.index_hit);

        List<Report> reports = new ArrayList<>();
        for (Checker checker : checkers) {
            reports.addAll(checker.check(project, sql));
        }

        return reports;
    }

    private void executeSql() {

        backgroundTaskQueue.run(new Task.Backgroundable(project, APPLICATION_NAME) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                executeSqlBackground();
            }
        });
    }

    private void executeSqlBackground() {

        ApplicationManager.getApplication().invokeLater(() -> {
            this.resultTabbedPane.remove(this.infoPane.getInfoPane());
            this.resultTabbedPane.remove(this.resultContentPane);

            this.resultTabbedPane.add(this.infoPane.getInfoPane());
            this.infoPane.setText("Executing statement...");
        });

        String sql = statementText.getText();

        MybatisSqlConfiguration sqlConfig = project.getService(MybatisSqlStateComponent.class).getConfiguration();

        if (StringUtils.isBlank(sql)) {
            if (!GlobalConfig.isMybatisMode) {
                ApplicationManager.getApplication().invokeLater(() -> this.infoPane.setText(INPUT_SQL_PROMPT));
                return;
            }
            ProcessResult<String> result = SqlParamGenerateComponent.generateSql(project);
            if (!result.isSuccess()) {
                ApplicationManager.getApplication().invokeLater(() -> this.infoPane.setText(result.getErrorMsg()));
                return;
            }
            sql = result.getData();
            ApplicationManager.getApplication().invokeLater(() -> statementText.setText(sqlConfig.getSql()));
        }

        try {
            BaseResult executeResult = SqlExecutor.executeSql(project, sql, true);
            SqlType sqlType = SqlParser.getExecuteSqlType(sql);
            if (sqlType == SqlType.select) {
                executeResultScroll.setVisible(true);

                executeResultTable.setModel(((SelectResult) executeResult).getModel());
            } else {
                executeResultScroll.setVisible(false);
            }

            executeInfoText.setText(ResultConverter.convert2ExecuteInfo(executeResult));

            ApplicationManager.getApplication().invokeLater(() -> {
                this.resultTabbedPane.remove(this.resultContentPane);
                this.resultTabbedPane.remove(this.infoPane.getInfoPane());
                this.resultTabbedPane.add(this.resultContentPane);
            });

            acquireExecuteIndex();

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String prompt = String.format("Execute Sql Failed.\n%s", sw);
            ApplicationManager.getApplication().invokeLater(() -> this.infoPane.setText(prompt));
        }
    }

    private void acquireExecuteIndex() throws Exception {

        MybatisSqlConfiguration sqlConfig = project.getService(MybatisSqlStateComponent.class).getConfiguration();

        //TODO
        String explainSql = String.format("explain %s", sqlConfig.getSql());
        SelectResult executeResult = (SelectResult) SqlExecutor.executeSql(project, explainSql, false);

        executeHitIndexTable.setModel(executeResult.getModel());

    }

    public JTabbedPane getSqlTabbedPanel() {
        return sqlTabbedPanel;
    }

    @Override
    public void listen() {
        this.sqlTabbedPanel.setSelectedIndex(0);
        updateSql(false);
    }

    private enum SqlComponentType {
        /**
         * Tanned类型对应的index
         */
        statement(0),
        result(1),
        stress(2);

        private final int index;

        SqlComponentType(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }

}
