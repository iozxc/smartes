package cn.omisheep.smartes.annotation;

import java.lang.annotation.*;

/**
 * @author zhouxinchen[1269670415@qq.com]
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Index {
    String value() default ""; // 索引名

    boolean auto() default true;
}
