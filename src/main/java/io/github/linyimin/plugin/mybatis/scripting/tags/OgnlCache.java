package io.github.linyimin.plugin.mybatis.scripting.tags;

import io.github.linyimin.plugin.mybatis.parsing.ParseException;
import ognl.Ognl;
import ognl.OgnlException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Caches OGNL parsed expressions.
 *
 * @author Eduardo Macarron
 *
 * @see <a href='https://github.com/mybatis/old-google-code-issues/issues/342'>Issue 342</a>
 */
public final class OgnlCache {

    private static final Map<String, Object> expressionCache = new ConcurrentHashMap<>();

    private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile("\\b\\w+\\.");

    private OgnlCache() {
        // Prevent Instantiation of Static Class
    }

    public static Object getValue(String expression, Object root) {
        try {
            Map context = Ognl.createDefaultContext(root);
            return Ognl.getValue(parseExpression(expression), context, root);
        } catch (OgnlException e) {
            throw new ParseException("Error evaluating expression '" + expression + "'. Cause: " + e, e);
        }
    }

    private static Object parseExpression(String expression) throws OgnlException {
        expression = removeTablePrefix(expression);

        Object node = expressionCache.get(expression);
        if (node == null) {
            node = Ognl.parseExpression(expression);
            expressionCache.put(expression, node);
        }
        return node;
    }

    private static String removeTablePrefix(String expression) {
        Matcher matcher = TABLE_PREFIX_PATTERN.matcher(expression);
        return matcher.replaceAll("");
    }
}
