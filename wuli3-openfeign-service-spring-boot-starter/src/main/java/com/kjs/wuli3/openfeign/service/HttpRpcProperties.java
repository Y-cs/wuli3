package com.kjs.wuli3.openfeign.service;

import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置显式启用的内部同步 HTTP RPC 服务入口。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@Getter
@Setter
@ConfigurationProperties("wuli3.http-rpc")
public class HttpRpcProperties {

    /** 默认关闭；启用时必须提供 HttpRpcAuthenticator Bean。 */
    private boolean enabled;

    /** 独占的 Servlet 前缀，不能为根路径或带通配符；网关不应将其暴露给外部调用方。 */
    private String path = HttpRpcProtocol.DEFAULT_PATH;
}
