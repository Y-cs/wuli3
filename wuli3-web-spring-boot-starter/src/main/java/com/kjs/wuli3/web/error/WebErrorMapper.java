package com.kjs.wuli3.web.error;

import com.kjs.wuli3.core.error.ErrorCodeException;
import org.jspecify.annotations.Nullable;

/**
 * 在 Web 输出边界为异常显式指定错误语义。
 *
 * 注意：按 Spring 排序依次调用，首个非空结果优先于框架默认分类。责任与可见性由返回的异常独立表达；
 * 返回 null 表示不处理。只用于 HTTP 输出，不改变内部 RPC 传播。
 *
 * @author 国杨 create on 2026/9/29 19:04
 */
@FunctionalInterface
public interface WebErrorMapper {

    /** 将异常映射为当前边界的错误语义；不处理时返回 null。 */
    @Nullable
    ErrorCodeException map(Throwable error);
}
