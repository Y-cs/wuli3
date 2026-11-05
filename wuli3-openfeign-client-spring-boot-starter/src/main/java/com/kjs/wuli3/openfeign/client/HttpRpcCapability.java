package com.kjs.wuli3.openfeign.client;

import com.kjs.wuli3.openfeign.client.error.HttpRpcErrorDecoder;
import feign.Capability;
import feign.Contract;
import feign.MethodMetadata;
import feign.Response;
import feign.ResponseInterceptor;
import java.util.List;
import java.util.Objects;

/**
 * 在 Feign 的返回值分支前恢复错误，防止 dismiss404 或 void 绕过错误协议。
 *
 * 注意：仅注册 ErrorDecoder 无法覆盖这些提前返回分支，因此同时约束方法契约及响应调用链。
 * 本类负责调用顺序，具体协议识别统一委托给配置注入的错误解码器，不自行创建第二套解码器。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
public final class HttpRpcCapability implements Capability {
    private final HttpRpcErrorDecoder errorDecoder;

    /** 使用当前 Feign 客户端已装配的解码器，对所有非成功响应执行一致的错误还原。 */
    public HttpRpcCapability(final HttpRpcErrorDecoder errorDecoder) {
        this.errorDecoder = Objects.requireNonNull(errorDecoder, "errorDecoder");
    }

    /** 拒绝原始 Response 返回类型，内部 RPC 只暴露业务返回值。 */
    @Override
    public Contract enrich(final Contract contract) {
        return type -> {
            final List<MethodMetadata> methods = contract.parseAndValidateMetadata(type);
            for (final MethodMetadata method : methods) {
                if (method.returnType() == Response.class) {
                    throw new IllegalStateException(
                            "Internal RPC does not support feign.Response: " + method.configKey());
                }
            }
            return methods;
        };
    }

    /** 所有非成功响应均先解码并关闭响应；成功响应交回原链完成业务 JSON 解码和资源释放。 */
    @Override
    public ResponseInterceptor.Chain enrich(final ResponseInterceptor.Chain chain) {
        return context -> {
            final Response response = context.response();
            if (response.status() < 200 || response.status() >= 300) {
                try {
                    throw this.errorDecoder.decode(response.request().url(), response);
                } finally {
                    response.close();
                }
            }
            return chain.next(context);
        };
    }
}
