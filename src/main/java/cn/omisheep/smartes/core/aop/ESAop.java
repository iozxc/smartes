package cn.omisheep.smartes.core.aop;

import cn.omisheep.smartes.annotation.Index;
import cn.omisheep.smartes.core.ElasticSearchService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;

import java.lang.reflect.ParameterizedType;
import java.util.Properties;

@Slf4j
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class,
        Object.class}))
public class ESAop implements Interceptor {

    private final ElasticSearchService elasticSearchService;

    public ESAop(ElasticSearchService elasticSearchService) {
        this.elasticSearchService = elasticSearchService;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object          proceed         = invocation.proceed();
        fun(mappedStatement.getId(), invocation.getArgs()[1]);
        return proceed;
    }


    public Object plugin(Object o) {
        return Plugin.wrap(o, this);
    }

    public void setProperties(Properties properties) {
    }

    @SneakyThrows
    private void fun(String method, Object parameter) {
        int    i          = method.lastIndexOf(".");
        String className  = method.substring(0, i);
        String methodName = method.substring(i + 1);

        ParameterizedType parameterizedType =
                (ParameterizedType) Class.forName(className)
                        .getGenericInterfaces()[0];

        Class<?> clz = (Class<?>) parameterizedType.getActualTypeArguments()[0];

        if (!elasticSearchService.isIndex(clz) || !clz.getAnnotation(Index.class).auto()) {
            return;
        }

        switch (methodName) {
            case "insert": {
                if (clz == parameter.getClass()) {
                    elasticSearchService.insert(parameter);
                }
                break;
            }
            case "updateById":
                Object o = ((MapperMethod.ParamMap) parameter).get("param1");
                if (clz == o.getClass()) {
                    elasticSearchService.insert(o);
                }
                break;
            case "deleteById": {
                elasticSearchService.delete(clz.getName(), parameter + "");
                break;
            }
        }
    }
}
