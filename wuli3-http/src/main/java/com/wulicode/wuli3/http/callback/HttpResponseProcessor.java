package com.wulicode.wuli3.http.callback;

import com.wulicode.wuli3.http.exchange.HttpExchangeContext;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * 在收到响应头后、向调用方交付前异步转换响应。
 *
 * 注意：同一实例可被并发请求复用，实现须保证线程安全。
 *
 * @author 国杨 create on 2026/11/16 10:35
 */
@FunctionalInterface
public interface HttpResponseProcessor {

    /**
     * 转换响应并返回一个元素；返回 null 或空 Mono 视为失败。
     *
     * 注意：消费或替换正文时，由实现负责有界聚合并释放旧正文，不得遗弃响应资源。
     */
    Mono<ClientResponse> process(final HttpExchangeContext context, final ClientResponse response);
}
