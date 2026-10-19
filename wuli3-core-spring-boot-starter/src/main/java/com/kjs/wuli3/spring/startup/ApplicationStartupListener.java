package com.kjs.wuli3.spring.startup;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 应用全部装配完成后输出启动标识和启动信息。
 *
 * 注意：字符画写入标准输出，不进入文件日志和结构化日志；启动信息通过 slf4j 记录。
 * 使用方式：由自动配置注册，应用无需额外声明 Bean。
 *
 * @author lipt create on 2026/10/19 10:35
 */
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationStartupListener.class);

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        final ApplicationContext context = event.getApplicationContext();
        final Environment environment = context.getEnvironment();
        final String serviceName = environment.getProperty("spring.application.name", "unknown");
        final String[] activeProfiles = environment.getActiveProfiles();
        final String profiles = activeProfiles.length == 0 ? "-" : String.join(",", activeProfiles);
        final int port = this.resolvePort(context);
        System.out.print(
                String.format(Locale.ROOT, """
                ============================================================
                  __        __  _   _  _      ___   _____
                  \\ \\      / / | | | || |    |_ _| |___ /
                   \\ \\ /\\ / /  | | | || |     | |    |_ \\
                    \\ V  V /   | |_| || |___  | |   ___) |
                     \\_/\\_/     \\___/ |_____||___| |____/
                ============================================================
                  服务启动成功
                  name: %s
                  webPort: %s
                  profiles: %s
                ============================================================
                """, serviceName, port < 0 ? "-" : Integer.toString(port), profiles));
        if (port < 0) {
            LOGGER.info("服务启动成功 name={} profiles={}", serviceName, profiles);
        } else {
            LOGGER.info("服务启动成功 name={} profiles={} port={}", serviceName, profiles, port);
        }
    }

    /**
     * 解析 Web 服务器实际绑定端口。
     *
     * 注意：配置 server.port=0 时返回随机分配后的实际端口。
     *
     * @param context 应用上下文
     * @return 实际端口；非 Web 应用返回 -1
     */
    int resolvePort(final ApplicationContext context) {
        if (context instanceof final WebServerApplicationContext webContext) {
            return webContext.getWebServer().getPort();
        }
        return -1;
    }
}
