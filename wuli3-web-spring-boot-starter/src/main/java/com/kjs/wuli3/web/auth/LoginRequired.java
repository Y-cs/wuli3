package com.kjs.wuli3.web.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要已认证用户的 Spring MVC 接口；类型级声明对全部处理方法生效。
 *
 * 注意：认证结果由 {@link AuthContextResolver} 提供，本注解只读取上下文是否存在，不执行认证。
 *
 * @author lipt create on 2026/10/20 10:00
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {}
