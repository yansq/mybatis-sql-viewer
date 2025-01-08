package io.github.linyimin.plugin.mybatis.scripting.tags;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.linyimin.plugin.component.SqlParamGenerateComponent;
import io.github.linyimin.plugin.mybatis.mapping.SqlSource;
import io.github.linyimin.plugin.sql.formatter.SqlFormatter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.linyimin.plugin.mybatis.scripting.tags.ForEachSqlNode.ITEM_PREFIX;

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

        DynamicContext context = new DynamicContext(types, parameterObject);

        rootSqlNode.apply(context);

        String sql = parameterize(context.getSql(), context);

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
        // #{user.username}
        // TODO 无法处理有多个param，且不同 param 中的变量重名的情况
        if (!param.contains(ITEM_PREFIX) && param.contains(".")) {
            realParam = realParam.substring(param.lastIndexOf(".") + 1);
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

}
