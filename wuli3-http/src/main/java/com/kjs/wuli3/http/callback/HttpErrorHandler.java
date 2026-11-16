package com.kjs.wuli3.http.callback;

import com.kjs.wuli3.http.exchange.HttpExchangeContext;

/**
 * 观察请求转换、网络交换、响应转换或正文传输阶段的错误。
 *
 * 注意：同一实例可被并发请求复用，实现须保证线程安全。
 *
 * @author 国杨 create on 2026/11/16 10:35
 */
@FunctionalInterface
public interface HttpErrorHandler {

    /** 处理 HTTP 阶段错误；处理器异常附加到原异常，不替换原始失败，也不恢复请求。 */
    void handle(final HttpExchangeContext context, final Throwable error);
}
