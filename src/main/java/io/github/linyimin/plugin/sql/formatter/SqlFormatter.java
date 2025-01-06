package io.github.linyimin.plugin.sql.formatter;

import com.github.vertical_blank.sqlformatter.core.FormatConfig;
import com.github.vertical_blank.sqlformatter.languages.Dialect;
import com.intellij.openapi.application.ApplicationManager;
import io.github.linyimin.plugin.configuration.DatasourceConfigComponent;

/**
 * @author banzhe
 * @date 2022/12/17 02:43
 **/
public class SqlFormatter {
    public static String format(String sql) {
        FormatConfig config = FormatConfig.builder()
                .indent("  ")
                .linesBetweenQueries(1)
                .build();

        DatasourceConfigComponent component = ApplicationManager.getApplication()
                .getComponent(DatasourceConfigComponent.class);
        String type = component.getType();
        Dialect dialect = "oracle".equals(type) ? Dialect.PlSql : Dialect.MySql;

        return com.github.vertical_blank.sqlformatter.SqlFormatter.of(dialect).format(sql, config);
    }
}
