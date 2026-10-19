package com.kjs.wuli3.spring.startup;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 注册应用启动日志监听器，所有引入核心 starter 的服务自动生效。
 *
 * @author lipt create on 2026/10/19 10:35
 */
@AutoConfiguration
@Import(ApplicationStartupListener.class)
public class StartupLogAutoConfiguration {}
