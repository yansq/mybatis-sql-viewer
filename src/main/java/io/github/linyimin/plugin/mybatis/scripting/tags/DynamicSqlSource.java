package io.github.linyimin.plugin.mybatis.scripting.tags;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.intellij.openapi.application.ApplicationManager;
import io.github.linyimin.plugin.component.SqlParamGenerateComponent;
import io.github.linyimin.plugin.configuration.DatasourceConfigComponent;
import io.github.linyimin.plugin.mybatis.mapping.SqlSource;
import io.github.linyimin.plugin.sql.formatter.SqlFormatter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.linyimin.plugin.constant.Constant.*;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

    private final SqlNode rootSqlNode;

    private final Pattern pattern = Pattern.compile("[$, #]\\{(.*?)}");

    public DynamicSqlSource(SqlNode rootSqlNode) {
        this.rootSqlNode = rootSqlNode;
    }

    @Override
    public String getSql(List<SqlParamGenerateComponent.ParamNameType> types, Object parameterObject) {

        // 所有参数的映射
        DynamicContext context = new DynamicContext(types, parameterObject);

        // 验证 tag 条件
        rootSqlNode.apply(context);

        String sql = parameterize(context.getSql(), context);

        // 包裹分页逻辑
        Object pageObject = context.getBindings().get(MYBAITS_PLUS_PAGE_KEY);
        if (pageObject instanceof JSONObject) {
            Page page = JSON.parseObject(pageObject.toString(), Page.class);
            sql = warpPageSql(sql, page);
        }

        return SqlFormatter.format(sql);
    }

    private String parameterize(String preparedStatement, DynamicContext context) {

        List<String> params = extractPlaceholder(preparedStatement);

        String parameterizeSql = preparedStatement;

        for (String param : params) {
            parameterizeSql = setParameter(parameterizeSql, param, context);
        }

        return parameterizeSql;

    }

    private String setParameter(String parameterizeSql, String param, DynamicContext context) {

        String realParam = param;
        // #{id:INTEGER}
        if (param.contains(":")) {
            realParam = realParam.substring(0, param.lastIndexOf(":"));
        }
        // #{elementIds, typeHandler=club.linyimin.dao.handler.ListToJsonTypeHandler}
        if (param.contains(",")) {
            realParam = realParam.substring(0, param.lastIndexOf(","));
        }

        Object value = context.getBindings().get(realParam);

        if (value == null) {
            return parameterizeSql;
        }

        Class<?> clazz = value.getClass();

        if (clazz == String.class || clazz == Character.class || clazz == Date.class || clazz == JSONObject.class || clazz == JSONArray.class) {
            value = "'" + value +"'";
        }

        return parameterizeSql.replaceAll("[$, #]\\{" + param + "}", value.toString());

    }

    private List<String> extractPlaceholder(String preparedStatement) {
        Matcher matcher = pattern.matcher(preparedStatement);

        List<String> params = new ArrayList<>();

        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                params.add(matcher.group(i));
            }
        }

        return params;
    }

    private String warpPageSql(String sql, Page page) {
        DatasourceConfigComponent component = ApplicationManager.getApplication()
                .getComponent(DatasourceConfigComponent.class);
        String type = component.getType();
        if ("oracle".equalsIgnoreCase(type)) {
            return String.format(
                    PAGE_ORACLE,
                    sql,
                    Math.max(1, page.getCurrent()) * page.getSize(),
                    Math.max(0, page.getCurrent() - 1) * page.getSize()
            );
        } else {
            return String.format(
                    PAGE_MYSQL,
                    sql,
                    Math.max(0, page.getCurrent() - 1) * page.getSize(),
                    page.getSize()
            );
        }
    }
}
