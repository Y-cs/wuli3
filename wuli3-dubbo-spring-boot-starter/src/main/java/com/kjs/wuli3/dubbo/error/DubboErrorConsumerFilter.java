package com.kjs.wuli3.dubbo.error;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * 把 Dubbo 响应附件中的稳定错误协议还原为唯一的 {@link ErrorCodeException}。
 *
 * <p>注意：错误附件缺失或非法时保留 Dubbo 原始异常，不根据不完整数据推断业务错误。
 *
 * @author GuoYang create on 2026/8/28 17:59
 */
@Slf4j
@Activate(group = CommonConstants.CONSUMER, order = 100)
public final class DubboErrorConsumerFilter implements Filter {
    private static final ErrorCodePropagator ERROR_PROPAGATION_ENCODER = new ErrorCodePropagator();

    /**
     * 在同步或异步响应完成后恢复错误协议，并记录调用失败。
     */
    @Override
    public Result invoke(final Invoker<?> invoker, final Invocation invocation) throws RpcException {
        final Result result;
        try {
            result = invoker.invoke(invocation);
        } catch (final RpcException exception) {
            DubboErrorConsumerFilter.logFailure(invoker, invocation, exception);
            throw exception;
        }
        if (result instanceof AsyncRpcResult asyncResult) {
            asyncResult.whenCompleteWithContext((response, failure) -> {
                if (response != null) {
                    DubboErrorConsumerFilter.handleResult(invoker, invocation, response);
                } else if (failure != null) {
                    DubboErrorConsumerFilter.logFailure(invoker, invocation, failure);
                }
            });
        } else {
            DubboErrorConsumerFilter.handleResult(invoker, invocation, result);
        }
        return result;
    }

    /** 恢复远程错误协议并记录失败结果。 */
    private static void handleResult(final Invoker<?> invoker, final Invocation invocation, final Result result) {
        DubboErrorConsumerFilter.translate(result);
        if (result.hasException()) {
            DubboErrorConsumerFilter.logFailure(invoker, invocation, result.getException());
        }
    }

    /** 记录失败 RPC 的服务、方法、远端地址和异常堆栈，不记录可能包含敏感信息的调用参数。 */
    private static void logFailure(final Invoker<?> invoker, final Invocation invocation, final Throwable exception) {
        final URL url = invoker.getUrl();
        final String remoteAddress = url == null ? "unknown" : url.getAddress();
        DubboErrorConsumerFilter.log.error(
                "Dubbo 调用失败: service={}, method={}, remoteAddress={}",
                invocation.getTargetServiceUniqueName(),
                invocation.getMethodName(),
                remoteAddress,
                exception);
    }

    /**
     * 使用完整且合法的错误附件替换 Dubbo 占位异常。
     */
    private static void translate(final Result result) {
        DubboErrorConsumerFilter.ERROR_PROPAGATION_ENCODER
                .extract(result::getAttachment)
                .ifPresent(protocol -> {
                    final Throwable remoteFailure = result.getException();
                    result.setException(new ErrorCodeException(protocol, remoteFailure));
                });
    }
}
