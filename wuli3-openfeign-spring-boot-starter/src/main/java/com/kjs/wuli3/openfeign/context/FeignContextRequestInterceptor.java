package com.kjs.wuli3.openfeign.context;

import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.store.ContextReader;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Objects;

/**
 * 在 Feign 客户端发起调用前把当前 Wuli3 上下文写入请求头。
 *
 * <p>注意：写入前先删除调用方已有的保留字段，防止业务代码伪造传播值；目标端必须属于可信内部边界。
 *
 * @author lipt create on 2026/11/4 11:20
 */
public final class FeignContextRequestInterceptor implements RequestInterceptor {

    private final ContextReader contextReader;
    private final ContextPropagator contextPropagator;

    /** 创建使用指定上下文读取与编码组件的拦截器。 */
    public FeignContextRequestInterceptor(
            final ContextReader contextReader, final ContextPropagator contextPropagator) {
        this.contextReader = Objects.requireNonNull(contextReader, "contextReader");
        this.contextPropagator = Objects.requireNonNull(contextPropagator, "contextPropagator");
    }

    /** 用当前上下文重建保留请求头；无上下文时保留字段被清空。 */
    @Override
    public void apply(final RequestTemplate template) {
        template.headers().keySet().stream()
                .filter(this.contextPropagator::isReservedFieldName)
                .toList()
                .forEach(template::header);
        this.contextPropagator.inject(ContextSnapshot.from(this.contextReader.state()), template::header);
    }
}
