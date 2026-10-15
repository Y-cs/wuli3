package com.kjs.wuli3.permission.annotation;

import com.kjs.wuli3.permission.model.PermissionCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明资源动作权限；同一调用上的重复声明及类、方法声明均须全部满足。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Permissions.class)
public @interface Permission {
    /** 完整资源引用，或指定资源枚举时的常量名；大小写敏感。 */
    String value();

    /** 权限枚举类型；未指定时 value 必须为“资源编码:常量名”。 */
    Class<? extends PermissionCode> resourceClass() default PermissionCode.class;
}
