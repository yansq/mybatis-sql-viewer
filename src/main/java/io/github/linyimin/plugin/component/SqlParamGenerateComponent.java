package io.github.linyimin.plugin.component;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import io.github.linyimin.plugin.ProcessResult;
import io.github.linyimin.plugin.cache.MybatisXmlContentCache;
import io.github.linyimin.plugin.configuration.MybatisSqlStateComponent;
import io.github.linyimin.plugin.constant.Constant;
import io.github.linyimin.plugin.mybatis.mapping.SqlSource;
import io.github.linyimin.plugin.mybatis.type.SimpleTypeRegistry;
import io.github.linyimin.plugin.mybatis.xml.XMLLanguageDriver;
import io.github.linyimin.plugin.mybatis.xml.XMLMapperBuilder;
import io.github.linyimin.plugin.pojo2json.DefaultPOJO2JSONParser;
import io.github.linyimin.plugin.pojo2json.POJO2JSONParser;
import io.github.linyimin.plugin.pojo2json.RandomPOJO2JSONParser;
import io.github.linyimin.plugin.provider.MapperXmlProcessor;
import io.github.linyimin.plugin.configuration.model.MybatisSqlConfiguration;
import io.github.linyimin.plugin.utils.JavaUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.github.linyimin.plugin.constant.Constant.MYBAITS_PLUS_PAGE_KEY;
import static io.github.linyimin.plugin.constant.Constant.MYBATIS_SQL_ANNOTATIONS;

/**
 * @author yiminlin
 * @date 2022/02/02 2:09 上午
 **/
public class SqlParamGenerateComponent {

    public static ProcessResult<MybatisSqlConfiguration> generate(PsiElement psiElement, POJO2JSONParser parser, boolean cache) {

        PsiMethod psiMethod = null;

        String statementId = "";

        if (psiElement instanceof PsiIdentifier && psiElement.getParent() instanceof PsiMethod) {

            psiMethod = (PsiMethod) psiElement.getParent();
            statementId = acquireMethodName(psiMethod);

        }

        if (psiElement instanceof XmlToken && psiElement.getParent() instanceof XmlTag) {
            List<PsiMethod> methods = MapperXmlProcessor.processMapperMethod(psiElement.getParent());
            psiMethod = methods.stream().findFirst().orElse(null);

            statementId = MapperXmlProcessor.acquireStatementId(psiElement.getParent());
        }

        MybatisSqlConfiguration sqlConfig = psiElement.getProject().getService(MybatisSqlStateComponent.class).getConfiguration();

        if (psiMethod == null) {

            if (cache) {
                sqlConfig.setPsiElement(psiElement);
                // 找不到对应的接口方法
                sqlConfig.setMethod(statementId);
                sqlConfig.setParams("{}");
                return ProcessResult.fail(String.format("method of %s is not exist.", statementId), sqlConfig);
            }

            MybatisSqlConfiguration configuration = new MybatisSqlConfiguration();
            configuration.setPsiElement(psiElement);
            configuration.setMethod(statementId);
            configuration.setParams("{}");

            return ProcessResult.success(configuration);

        }

        String params = generateMethodParam(psiMethod, parser);
        boolean isRowBounds = isRowBounds(psiMethod);

        if (cache) {
            sqlConfig.setPsiElement(psiElement);
            sqlConfig.setMethod(statementId);
            sqlConfig.setParams(params);
            sqlConfig.setUpdateSql(true);
            if (parser instanceof RandomPOJO2JSONParser) {
                sqlConfig.setDefaultParams(false);
            }
            if (parser instanceof DefaultPOJO2JSONParser) {
                sqlConfig.setDefaultParams(true);
            }

            return ProcessResult.success(sqlConfig);
        }

        MybatisSqlConfiguration configuration = new MybatisSqlConfiguration();
        configuration.setPsiElement(psiElement);
        configuration.setMethod(statementId);
        configuration.setParams(params);

        return ProcessResult.success(configuration);
    }

    private static boolean isRowBounds(PsiMethod method) {
        List<ParamNameType> paramNameTypes = getMethodBodyParamList(method);
        return paramNameTypes.stream().anyMatch(paramNameType -> {
           String classQualifier = paramNameType.psiType.getCanonicalText();
           return StringUtils.equals(classQualifier, "org.apache.ibatis.session.RowBounds");
        });
    }

    public static ProcessResult<String> generateSql(Project project, String methodQualifiedName, String params, boolean cache) {

        MybatisSqlConfiguration sqlConfig = project.getService(MybatisSqlStateComponent.class).getConfiguration();

        try {
            ProcessResult<String> processResult = ApplicationManager.getApplication().runReadAction((Computable<? extends ProcessResult<String>>) () -> getSqlFromAnnotation(project, methodQualifiedName, params));
            if (processResult.isSuccess()) {

                if (cache) {
                    sqlConfig.setSql(processResult.getData());
                    sqlConfig.setUpdateSql(false);
                }

                return processResult;
            }

            processResult = ApplicationManager.getApplication().runReadAction((Computable<? extends ProcessResult<String>>) () -> getSqlFromXml(project, methodQualifiedName, params));

            if (processResult.isSuccess() && cache) {
                sqlConfig.setSql(processResult.getData());
                sqlConfig.setUpdateSql(false);
            }

            return processResult;

        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));

            return ProcessResult.fail(String.format("generate sql error. \n%s", sw));
        }
    }

    public static ProcessResult<String> generateSql(Project project) {
        MybatisSqlConfiguration sqlConfig = project.getService(MybatisSqlStateComponent.class).getConfiguration();
        if (StringUtils.isBlank(sqlConfig.getMethod())) {
            return ProcessResult.fail("Please select a mybatis method");
        }
        return generateSql(project, sqlConfig.getMethod(), sqlConfig.getParams(), true);
    }

    private static ProcessResult<String> getSqlFromAnnotation(Project project, String qualifiedMethod, String params) {
        // 处理annotation
        String clazzName = qualifiedMethod.substring(0, qualifiedMethod.lastIndexOf("."));
        String methodName = qualifiedMethod.substring(qualifiedMethod.lastIndexOf(".") + 1);

        List<PsiMethod> psiMethods = JavaUtils.findMethod(project, clazzName, methodName);

        if (psiMethods.isEmpty()) {
            return ProcessResult.fail("annotation is not exist.");
        }

        PsiAnnotation[] annotations = psiMethods.get(0).getAnnotations();
        PsiAnnotation annotation = Arrays.stream(annotations).filter(psiAnnotation -> MYBATIS_SQL_ANNOTATIONS.contains(psiAnnotation.getQualifiedName())).findFirst().orElse(null);

        if (annotation == null) {
            return ProcessResult.fail("There is no of annotation on the method.");
        }

        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            return ProcessResult.success("The annotation does not specify a value for the value field");
        }

        String content = String.valueOf(JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(value));

        if (StringUtils.isBlank(content)) {
            return ProcessResult.success("The value of annotation is empty.");
        }

        String sql = new XMLLanguageDriver().createSqlSource(content).getSql(getMethodBodyParamList(psiMethods.get(0)), params);

        return ProcessResult.success(sql);
    }

    private static ProcessResult<String> getSqlFromXml(Project project, String qualifiedMethod, String params) {
        try {

            String namespace = qualifiedMethod.substring(0, qualifiedMethod.lastIndexOf("."));

            Optional<String> optional = MybatisXmlContentCache.acquireByNamespace(project, namespace)
                    .stream()
                    .map(XmlTag::getText)
                    .findFirst();

            if (optional.isEmpty()) {
                return ProcessResult.fail("Oops! The plugin can't find the mapper file.");
            }
            // 去除注释，避免中文注释解析问题
            String mapperString = optional.get().replaceAll("(?s)<!--.*?-->", "");
            XMLMapperBuilder builder = new XMLMapperBuilder(new ByteArrayInputStream(mapperString.getBytes(StandardCharsets.UTF_8)));
            Map<String, SqlSource> sqlSourceMap = builder.parse();

            if (!sqlSourceMap.containsKey(qualifiedMethod)) {
                return ProcessResult.fail(String.format("Oops! There is not %s in mapper file!!!", qualifiedMethod));
            }

            String methodName = qualifiedMethod.substring(qualifiedMethod.lastIndexOf(".") + 1);
            List<PsiMethod> psiMethods = JavaUtils.findMethod(project, namespace, methodName);
            List<ParamNameType> types = Collections.emptyList();
            if (CollectionUtils.isNotEmpty(psiMethods)) {
                types = getMethodBodyParamList(psiMethods.get(0));
            }

            String sql = sqlSourceMap.get(qualifiedMethod).getSql(types, params);
            return ProcessResult.success(sql);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            return ProcessResult.fail(String.format("Oops! There are something wrong when generate sql statement.\n%s", sw));
        }

    }

    private static String acquireMethodName(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        assert psiClass != null;

        String methodName = method.getName();
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName + "." + methodName;

    }

    private static String generateMethodParam(PsiMethod method, POJO2JSONParser parser) {

        List<ParamNameType> paramNameTypes = getMethodBodyParamList(method);

        Map<String, Object> params = new HashMap<>();
        for (ParamNameType paramNameType : paramNameTypes) {
            PsiType type = paramNameType.psiType;
            String name = paramNameType.name;

            Object value = parser.parseFieldValueType(type, 0, new ArrayList<>(), new HashMap<>());

            if (value instanceof Map && paramNameTypes.size() == 1) {
                params.putAll((Map) value);
            } else if (value instanceof Page<?>) {
                params.put(MYBAITS_PLUS_PAGE_KEY , value);
            } else {
                params.put(name, value);
            }
        }

        if (paramNameTypes.size() == 1) {
            PsiType type = paramNameTypes.get(0).getPsiType();
            if (SimpleTypeRegistry.isSimpleType(type.getCanonicalText()) || type instanceof PsiArrayType) {
                return new GsonBuilder().setPrettyPrinting().create().toJson(params);
            }

            // 当只有一个 reference 类型参数时，使用该参数的变量名包裹: {name: aaa, age: 17} -> {user: {name: aaa, age: 17}}
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject jsonObject = new JsonObject();
            String parameterName = paramNameTypes.get(0).name;
            jsonObject.add(parameterName, gson.toJsonTree(params));
            return gson.toJson(jsonObject);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(params);
    }

    /**
     * 获取方法所有参数
     * @param psiMethod {@link PsiMethod}
     * @return param list {@link ParamNameType}
     */
    public static List<ParamNameType> getMethodBodyParamList(PsiMethod psiMethod) {
        List<ParamNameType> result = new ArrayList<>();
        PsiParameterList parameterList = psiMethod.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        for (PsiParameter param : parameters) {
            String paramAnnotationValue = getParamAnnotationValue(param);
            String name = StringUtils.isBlank(paramAnnotationValue) ? param.getName() : paramAnnotationValue;
            if (!StringUtils.equals(param.getType().getCanonicalText(), "org.apache.ibatis.session.RowBounds")) {
                result.add(new ParamNameType(name, param.getType()));
            }
        }
        return result;
    }

    /**
     * 获取Param注解的value
     * @param param {@link PsiParameter}
     * @return {org.apache.ibatis.annotations.Param} value的值
     */
    private static String getParamAnnotationValue(PsiParameter param) {
        PsiAnnotation annotation = param.getAnnotation(Constant.PARAM_ANNOTATION);
        if (Objects.isNull(annotation)) {
            return StringUtils.EMPTY;
        }
        List<PsiNameValuePair> nameValuePairs = Lists. newArrayList(annotation.getParameterList().getAttributes());

        return nameValuePairs.stream()
                .map(PsiNameValuePair::getLiteralValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(StringUtils.EMPTY);
    }


    public static class ParamNameType {
        private final String name;
        private final PsiType psiType;

        public ParamNameType(String name, PsiType psiType) {
            this.name = name;
            this.psiType = psiType;
        }

        public PsiType getPsiType() {
            return this.psiType;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
