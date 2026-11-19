package com.kjs.wuli3.http.webclient;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.http.logging.HttpLogRecorder;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 单次构建的初始化上下文；仅在构建线程中使用，不得在构建完成后继续修改 builder。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public record InitializationContext(WebClientConfig config, WebClient.Builder builder, HttpLogRecorder logRecorder) {

    /** 校验初始化上下文的必要依赖。 */
    public InitializationContext {
        Asserts.whenNull(config).throwIllegalArgumentException("初始化配置不能为空");
        Asserts.whenNull(builder).throwIllegalArgumentException("原生构建器不能为空");
        Asserts.whenNull(logRecorder).throwIllegalArgumentException("日志接收器不能为空");
    }
}
