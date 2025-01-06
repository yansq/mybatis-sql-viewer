package io.github.linyimin.plugin.sql.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.intellij.openapi.application.ApplicationManager;
import io.github.linyimin.plugin.ProcessResult;
import io.github.linyimin.plugin.configuration.DatasourceConfigComponent;
import io.github.linyimin.plugin.sql.checker.enums.CheckScopeEnum;
import net.sf.jsqlparser.util.validation.Validation;
import net.sf.jsqlparser.util.validation.ValidationError;
import net.sf.jsqlparser.util.validation.ValidationException;
import net.sf.jsqlparser.util.validation.feature.DatabaseType;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yiminlin
 * @date 2022/11/27 21:43
 **/
public class SqlParser {
    public static List<String> getTableNames(String sql) {
        SQLStatement statement = parseStatement(sql);
        SchemaStatVisitor statVisitor = createStatVisitor();
        statement.accept(statVisitor);

        return statVisitor.getOriginalTables().stream()
                .map(SQLName::getSimpleName)
                .map(table -> table.replaceAll("`", "")).distinct().collect(Collectors.toList());
    }

    public static SqlType getExecuteSqlType(String sql) {
        SQLStatement statement = parseStatement(sql);

        if (statement instanceof SQLUpdateStatement || statement instanceof SQLInsertStatement || statement instanceof SQLDeleteStatement) {
            return SqlType.update;
        }

        return SqlType.select;
    }

    public static CheckScopeEnum getCheckScope(String sql) {
        try {
            SQLStatement statement = parseStatement(sql);
            if (statement instanceof SQLUpdateStatement) {
                return CheckScopeEnum.update;
            }
            if (statement instanceof SQLSelectStatement) {
                return CheckScopeEnum.select;
            }
            if (statement instanceof  SQLDeleteStatement) {
                return CheckScopeEnum.delete;
            }
            if (statement instanceof SQLInsertStatement) {
                return CheckScopeEnum.insert;
            }
            return CheckScopeEnum.none;
        } catch (Exception e) {
            return CheckScopeEnum.none;
        }
    }

    public static ProcessResult<String> validate(String sql) {
        if (StringUtils.isBlank(sql)) {
            return ProcessResult.fail("sql statement is blank. Please input sql statement.");
        }

        DatabaseType databaseType = getDatabaseType();
        Validation validation = new Validation(Collections.singletonList(databaseType), sql);
        ValidationException exception = validation.validate().stream()
                .map(ValidationError::getErrors).flatMap(Set::stream).findFirst().orElse(null);

        if (exception == null) {
            return ProcessResult.success(null);
        }
        return ProcessResult.fail(exception.getMessage());
    }

    private static SQLStatement parseStatement(String sql) {
        System.out.println("sql: " + sql);
        return SQLUtils.parseSingleStatement(sql, getDbType());
    }

    private static SchemaStatVisitor createStatVisitor() {
        return SQLUtils.createSchemaStatVisitor(getDbType());
    }

    private static DbType getDbType() {
        DatasourceConfigComponent component = ApplicationManager.getApplication()
                .getComponent(DatasourceConfigComponent.class);
        DbType dbType =  DbType.of(component.getType());
        return dbType == null ? DbType.mysql : dbType;
    }

    private static DatabaseType getDatabaseType() {
        DatasourceConfigComponent component = ApplicationManager.getApplication()
                .getComponent(DatasourceConfigComponent.class);
        DatabaseType databaseType = DatabaseType.get(component.getType());
        return databaseType == null ? DatabaseType.MYSQL : databaseType;
    }
}