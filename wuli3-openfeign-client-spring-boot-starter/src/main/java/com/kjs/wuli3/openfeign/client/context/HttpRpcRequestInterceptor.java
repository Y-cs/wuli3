package com.kjs.wuli3.openfeign.client.context;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * 从当前调用作用域写入白名单上下文，覆盖业务方伪造的保留字段。
 *
 * 注意：只捕获当前快照，不安装或修改本地作用域；服务身份认证头由应用另外配置。
 * 先清理所有保留头，再写入快照及明文版本，防止复用请求模板时泄漏上次调用的身份。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
@NullMarked
public final class HttpRpcRequestInterceptor implements RequestInterceptor {
    private final ContextManager manager;
    private final ContextPropagator propagator;

    /** 使用共享上下文管理器与编解码器。 */
    public HttpRpcRequestInterceptor(final ContextManager manager, final ContextPropagator propagator) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.propagator = Objects.requireNonNull(propagator, "propagator");
    }

    /**
     * 清理保留字段后注入快照，普通业务头保留；字段使用 UTF-8 的无填充 Base64URL 编码。
     *
     * 注意：同时检查原值和编码后长度，字符数合法不代表其 UTF-8 编码符合传输大小限制。
     */
    @Override
    public void apply(final RequestTemplate template) {
        for (final String name : List.copyOf(template.headers().keySet())) {
            if (this.propagator.isReservedFieldName(name)) {
                template.removeHeader(name);
            }
        }
        this.propagator.inject(this.manager.capture(), (name, value) -> {
            Asserts.whenTrue(value.length() > HttpRpcProtocol.MAX_HEADER_LENGTH
                            || value.chars().anyMatch(character -> character < 32 || character == 127))
                    .throwIllegalArgumentException("Invalid HTTP RPC context header");
            final String encoded =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            Asserts.whenTrue(encoded.length() > HttpRpcProtocol.MAX_HEADER_LENGTH)
                    .throwIllegalArgumentException("HTTP RPC context exceeds header limit");
            template.headerLiteral(name, encoded);
        });
        template.header(HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION);
    }
}
