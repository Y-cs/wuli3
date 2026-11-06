package com.kjs.wuli3.openfeign.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 显式导出只供内部 HTTP RPC Servlet 发现的同步 JSON 服务。
 *
 * 注意：方法使用 Spring 的请求映射注解，路径相对于内部 Servlet；不得同时标记 Controller 或 RestController，
 * 避免被公开 MVC 暴露。实例的依赖注入、代理和生命周期仍由应用容器管理；需要 AOP 时使用类代理，不支持 JDK 动态代理。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@Documented
@Component
@ResponseBody
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpRpcService {}
