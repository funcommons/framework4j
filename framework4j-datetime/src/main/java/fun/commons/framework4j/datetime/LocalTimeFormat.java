package fun.commons.framework4j.datetime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 1. 定义注解
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalTimeFormat {
        // 标记该接口返回的时间格式为 "yyyy-MM-dd HH:mm:ss"
    }