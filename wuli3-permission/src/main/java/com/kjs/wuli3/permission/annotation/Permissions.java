package com.kjs.wuli3.permission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可重复权限注解的容器。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permissions {
    /** 返回共同生效的权限声明。 */
    Permission[] value();
}
