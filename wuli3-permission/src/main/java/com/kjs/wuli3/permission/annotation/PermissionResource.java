package com.kjs.wuli3.permission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识权限枚举的资源及描述；自身不触发鉴权。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionResource {
    /** 资源编码；只能由大写英文字母、数字和下划线组成。 */
    String value();

    /** 资源的业务意义；必须非空白。 */
    String description();
}
