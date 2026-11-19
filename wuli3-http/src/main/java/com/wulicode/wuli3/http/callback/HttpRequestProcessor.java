package com.wulicode.wuli3.http.callback;

import com.wulicode.wuli3.http.exchange.HttpExchangeContext;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;

/**
 * 在每次订阅的网络交换前异步转换请求。
 *
 * 注意：同一实例可被并发请求复用，实现须保证线程安全。
 *
 * @author 国杨 create on 2026/11/16 10:35
 */
@FunctionalInterface
public interface HttpRequestProcessor {

    /** 转换请求并返回一个元素；保持原请求时返回 Mono.just(request)，返回 null 或空 Mono 视为失败。 */
    Mono<ClientRequest> process(final HttpExchangeContext context, final ClientRequest request);
}
