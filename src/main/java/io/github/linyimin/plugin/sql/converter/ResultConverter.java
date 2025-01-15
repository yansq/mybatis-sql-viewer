package io.github.linyimin.plugin.sql.converter;

import io.github.linyimin.plugin.constant.Constant;
import io.github.linyimin.plugin.settings.SqlViewerSettingsState;
import io.github.linyimin.plugin.sql.checker.Report;
import io.github.linyimin.plugin.sql.checker.enums.CheckScopeEnum;
import io.github.linyimin.plugin.sql.checker.enums.LevelEnum;
import io.github.linyimin.plugin.sql.result.BaseResult;
import io.github.linyimin.plugin.sql.result.InsertResult;
import io.github.linyimin.plugin.sql.result.SelectResult;
import io.github.linyimin.plugin.sql.result.UpdateResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.table.DefaultTableModel;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * @author yiminlin
 * @date 2022/11/27 20:45
 **/
public class ResultConverter {

    public static DefaultTableModel convert2TableModel(ResultSet rs) throws SQLException {

        ResultSetMetaData metaData = rs.getMetaData();

        SqlViewerSettingsState state = SqlViewerSettingsState.getInstance();
        int maxRowsReturned = state.maxRowsReturnedField;

        // names of columns
        Vector<String> columnNames = new Vector<>();
        int columnCount = metaData.getColumnCount();
        for (int column = 1; column <= columnCount; column++) {
            columnNames.add(metaData.getColumnLabel(column));
        }

        // data of the table
        Vector<Vector<Object>> data = new Vector<>();
        int count = 0;
        while (rs.next()) {
            Vector<Object> vector = new Vector<>();
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                vector.add(rs.getObject(columnIndex));
            }
            data.add(vector);
            if (++count >= maxRowsReturned) {
                break;
            }
        }

        rs.close();

        return new DefaultTableModel(data, columnNames);
    }

    public static String convert2ExecuteInfo(BaseResult result) {
        StringBuilder sb = new StringBuilder("------[Execution Succeeded]------\n");

        if (StringUtils.isNotBlank(result.getSql())) {
            sb.append("[statement]: ").append(result.getSql()).append("\n");
        }

        sb.append("[cost]: ").append(result.getCost()).append("(ms)").append("\n");
        if (result instanceof UpdateResult) {
            sb.append("[Rows Affected]: ").append(((UpdateResult) result).getAffectedCount()).append("\n");
        } else {
            sb.append("[Return Rows]: ").append(((SelectResult)result).getModel().getRowCount()).append("\n");
        }

        if (result.getTotalRows().size() == 1) {
            sb.append("[Total Rows]: ").append(result.getTotalRows().get(0).getValue()).append("\n");
        } else {
            for (Pair<String, Long> pair : result.getTotalRows()) {
                String table = pair.getKey().replaceAll("`", "");
                sb.append("[Total Rows(").append(table).append(")]: ").append(pair.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

    public static String convert2InsertInfo(InsertResult result) {

        return "------[Insertion Succeeded]------\n"
                + "[Cost]: " + result.getCost() + "(ms)\n"
                + "[Rows Affected]: " + result.getAffectedCount() + "\n"
                + "[Total Rows]: " + result.getTotalRows().get(0).getValue() + "\n";
    }

    public static String convert2RuleInfo(CheckScopeEnum scope, List<Report> reports) {

        List<Report> noPassReports = reports.stream().filter(report -> !report.isPass()).collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();

        if (CollectionUtils.isEmpty(noPassReports)) {
            noPassReports.addAll(Constant.DEFAULT_REPORT_MAP.getOrDefault(scope, new ArrayList<>()));
            if (CollectionUtils.isEmpty(noPassReports)) {
                sb.append("符合SQL规范要求");
                return sb.toString();
            }
            sb.append("符合SQL规范要求, 以下信息仅供参考：\n").append("\n");
        } else {
            Optional<Report> optional = noPassReports.stream().filter(report -> report.getLevel() == LevelEnum.error).findAny();
            if (optional.isPresent()) {
                return sb.append("Check rule error.\n").append(optional.get().getDesc()).toString();
            }
            sb.append("SQL可能不满足规范要求：\n").append("\n");
        }

        for (int i = 0; i < noPassReports.size(); i++) {
            Report report = noPassReports.get(i);
            sb.append(i + 1).append(".【").append(report.getLevel().name()).append("】");
            sb.append(report.getDesc()).append("\n");
            if (StringUtils.isNotBlank(report.getSample())) {
                sb.append("  【sample】\n").append("    ").append(report.getSample()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
