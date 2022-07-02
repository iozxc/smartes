package cn.omisheep.smartes.annotation;

import java.lang.annotation.*;

/**
 * @author zhouxinchen[1269670415@qq.com]
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IndexField {
    String value() default ""; // 字段名，默认为属性名

    String type() default ""; // 字段类型，如text等，可以不设置，会默认根据java类型来转换

    String analyzer() default "ik_max_word"; // 在类型为text时生效

    String searchAnalyzer() default "ik_max_word"; // 在类型为text时生效

    /**
     * 默认为true参与搜索。
     * false时不参与搜索，只作为内容传递，但在指定should和must搜索下仍然能被搜索
     *
     * @return 默认为true参与搜索。
     */
    boolean isSearch() default true;
}
