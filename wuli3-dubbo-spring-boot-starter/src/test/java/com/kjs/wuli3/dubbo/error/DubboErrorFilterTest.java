package com.kjs.wuli3.dubbo.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import com.kjs.wuli3.dubbo.autoconfigure.DubboProperties;
import java.util.concurrent.CompletableFuture;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;

/**
 * 验证 Dubbo 错误 Filter 的跨服务完整传播和原生异常保留规则。
 *
 * @author GuoYang create on 2026/8/28 17:59
 */
class DubboErrorFilterTest {
    /** 验证可信 RPC 对四种可见性均完整传播错误，并保留最终出口使用的策略。 */
    @Test
    void providerPreservesAllVisibilityPolicies() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        for (final ErrorVisibility visibility : ErrorVisibility.values()) {
            final ErrorCodeException exception =
                    new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "denied").withVisibility(visibility);
            final Result wireResult =
                    DubboErrorFilterTest.invokeProvider(properties, invocation, "policy-service", exception);
            assertThat(wireResult.getAttachment(ErrorCodePropagator.ORIGINAL_CODE))
                    .isEqualTo("POLICY-SERVICE.COMMON.ILLEGAL_ARGUMENT");
            assertThat(wireResult.getAttachment(ErrorCodePropagator.CODE))
                    .isEqualTo("POLICY-SERVICE.COMMON.ILLEGAL_ARGUMENT");
            assertThat(wireResult.getAttachment(ErrorCodePropagator.MESSAGE)).isEqualTo("denied");
            assertThat(wireResult.getAttachment(ErrorCodePropagator.VISIBILITY)).isEqualTo(visibility.name());
            final String visibleCode = wireResult.getAttachment(ErrorCodePropagator.CODE);
            final String visibleMessage = wireResult.getAttachment(ErrorCodePropagator.MESSAGE);
            final Invoker<?> consumerInvoker = mock(Invoker.class);
            when(consumerInvoker.invoke(invocation)).thenReturn(wireResult);
            final DubboErrorConsumerFilter consumer = new DubboErrorConsumerFilter();
            consumer.setDubboProperties(properties);
            final Result localResult = consumer.invoke(consumerInvoker, invocation);
            assertThat(localResult.getException()).isInstanceOfSatisfying(ErrorCodeException.class, translated -> {
                assertThat(translated.getErrorCode()).isInstanceOfSatisfying(ErrorCodeCarrier.class, carrier -> {
                    assertThat(carrier.originalCode()).isEqualTo("POLICY-SERVICE.COMMON.ILLEGAL_ARGUMENT");
                    assertThat(carrier.code()).isEqualTo(visibleCode);
                    assertThat(carrier.message()).isEqualTo(visibleMessage);
                    assertThat(carrier.origin()).isEqualTo(ErrorOrigin.CALLER);
                    assertThat(carrier.severity()).isEqualTo(ErrorSeverity.NORMAL);
                    assertThat(carrier.visibility()).isEqualTo(visibility);
                    assertThat(carrier.sourceService()).isEqualTo("policy-service");
                });
            });
        }
    }

    /** 验证多跳 RPC 保留完整错误和最初来源，不提前执行对外展示策略。 */
    @Test
    void secondProviderHopPreservesFullErrorAndVisibility() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        final Result firstWire = DubboErrorFilterTest.invokeProvider(
                properties,
                invocation,
                "first-service",
                new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "denied")
                        .withVisibility(ErrorVisibility.MESSAGE_ONLY));
        final Invoker<?> consumerInvoker = mock(Invoker.class);
        when(consumerInvoker.invoke(invocation)).thenReturn(firstWire);
        final DubboErrorConsumerFilter consumer = new DubboErrorConsumerFilter();
        consumer.setDubboProperties(properties);
        final ErrorCodeException remote = (ErrorCodeException)
                consumer.invoke(consumerInvoker, invocation).getException();

        final Result secondWire = DubboErrorFilterTest.invokeProvider(properties, invocation, "second-service", remote);
        assertThat(secondWire.getAttachment(ErrorCodePropagator.CODE))
                .isEqualTo("FIRST-SERVICE.COMMON.ILLEGAL_ARGUMENT");
        assertThat(secondWire.getAttachment(ErrorCodePropagator.ORIGINAL_CODE))
                .isEqualTo("FIRST-SERVICE.COMMON.ILLEGAL_ARGUMENT");
        assertThat(secondWire.getAttachment(ErrorCodePropagator.MESSAGE)).isEqualTo("denied");
        assertThat(secondWire.getAttachment(ErrorCodePropagator.SOURCE_SERVICE)).isEqualTo("first-service");
        assertThat(secondWire.getAttachment(ErrorCodePropagator.ORIGIN)).isEqualTo("CALLER");
        assertThat(secondWire.getAttachment(ErrorCodePropagator.SEVERITY)).isEqualTo("NORMAL");
        assertThat(secondWire.getAttachment(ErrorCodePropagator.VISIBILITY)).isEqualTo("MESSAGE_ONLY");
    }

    /** 缺少原始错误码字段时保留 Dubbo 原始异常，避免根据不完整附件重建错误。 */
    @Test
    void consumerRejectsPropagationWithoutOriginalCode() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        final RuntimeException original = new RuntimeException("wire failure");
        final AppResponse response = new AppResponse(original);
        response.setAttachment(ErrorCodePropagator.CODE, "SERVICE.SYSTEM.INTERNAL_ERROR");
        response.setAttachment(ErrorCodePropagator.MESSAGE, "内部错误");
        response.setAttachment(ErrorCodePropagator.ORIGIN, "SERVER");
        response.setAttachment(ErrorCodePropagator.SEVERITY, "CRITICAL");
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenReturn(response);
        final DubboErrorConsumerFilter consumer = new DubboErrorConsumerFilter();
        consumer.setDubboProperties(properties);

        assertThat(consumer.invoke(invoker, invocation).getException()).isSameAs(original);
    }

    /** 验证消费方无需提供方业务枚举即可恢复完整错误码和生效策略。 */
    @Test
    void providerAndConsumerTranslateErrorWithoutSharingBusinessEnum() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        final Invoker<?> providerInvoker = mock(Invoker.class);
        when(providerInvoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service?application=group-service"));
        when(providerInvoker.invoke(invocation))
                .thenReturn(new AppResponse(new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "denied")
                        .withVisibility(ErrorVisibility.CODE_ONLY)));
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(properties);

        final Result wireResult = provider.invoke(providerInvoker, invocation);

        assertThat(wireResult.getException())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Remote service invocation failed");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.CODE))
                .isEqualTo("GROUP-SERVICE.COMMON.ILLEGAL_ARGUMENT");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.ORIGINAL_CODE))
                .isEqualTo("GROUP-SERVICE.COMMON.ILLEGAL_ARGUMENT");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.MESSAGE)).isEqualTo("denied");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.ORIGIN)).isEqualTo("CALLER");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.SEVERITY)).isEqualTo("NORMAL");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.SOURCE_SERVICE)).isEqualTo("group-service");
        final Invoker<?> consumerInvoker = mock(Invoker.class);
        when(consumerInvoker.invoke(invocation)).thenReturn(wireResult);
        final DubboErrorConsumerFilter consumer = new DubboErrorConsumerFilter();
        consumer.setDubboProperties(properties);

        final Result localResult = consumer.invoke(consumerInvoker, invocation);

        assertThat(localResult.getException()).isInstanceOfSatisfying(ErrorCodeException.class, exception -> {
            assertThat(exception.getErrorCode()).isInstanceOfSatisfying(ErrorCodeCarrier.class, propagated -> {
                assertThat(propagated.code()).isEqualTo("GROUP-SERVICE.COMMON.ILLEGAL_ARGUMENT");
                assertThat(propagated.originalCode()).isEqualTo("GROUP-SERVICE.COMMON.ILLEGAL_ARGUMENT");
                assertThat(propagated.message()).isEqualTo("denied");
                assertThat(propagated.visibility()).isEqualTo(ErrorVisibility.CODE_ONLY);
                assertThat(propagated.origin()).isEqualTo(ErrorOrigin.CALLER);
                assertThat(propagated.severity()).isEqualTo(ErrorSeverity.NORMAL);
                assertThat(propagated.sourceService()).isEqualTo("group-service");
            });
            assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
        });
    }

    /** 普通异常结果保持原异常及附件，交由 Dubbo 原生机制处理。 */
    @Test
    void providerAndConsumerPreserveUnknownJavaException() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        final IllegalArgumentException original = new IllegalArgumentException("native failure");
        final AppResponse response = new AppResponse(original);
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service?application=group"));
        when(invoker.invoke(invocation)).thenReturn(response);
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(properties);
        final DubboErrorConsumerFilter consumer = new DubboErrorConsumerFilter();
        consumer.setDubboProperties(properties);

        assertThat(provider.invoke(invoker, invocation)).isSameAs(response);
        assertThat(consumer.invoke(invoker, invocation).getException()).isSameAs(original);
        assertThat(response.getAttachment(ErrorCodePropagator.CODE)).isNull();
    }

    /** 同步抛出的普通异常不能被过滤器变成失败结果。 */
    @Test
    void providerPreservesSynchronousNativeThrow() {
        final Invocation invocation = mock(Invocation.class);
        final IllegalArgumentException original = new IllegalArgumentException("native failure");
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service"));
        when(invoker.invoke(invocation)).thenThrow(original);
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(new DubboProperties());

        assertThatThrownBy(() -> provider.invoke(invoker, invocation)).isSameAs(original);
    }

    /** 同步抛出的错误码异常仍进入稳定协议，并完整保留内部消息。 */
    @Test
    void providerTranslatesSynchronousErrorCodeThrow() {
        final Invocation invocation = mock(RpcInvocation.class);
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service"));
        when(invoker.invoke(invocation))
                .thenThrow(new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "internal")
                        .withVisibility(ErrorVisibility.INTERNAL));
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(new DubboProperties());

        final Result result = provider.invoke(invoker, invocation);

        assertThat(result.getAttachment(ErrorCodePropagator.CODE)).isEqualTo("COMMON.ILLEGAL_ARGUMENT");
        assertThat(result.getAttachment(ErrorCodePropagator.MESSAGE)).isEqualTo("internal");
        assertThat(result.getAttachment(ErrorCodePropagator.VISIBILITY)).isEqualTo("INTERNAL");
    }

    /** 延迟完成的异步错误码结果在两端完成回调中传播并恢复。 */
    @Test
    void filtersTranslateDeferredErrorCodeResponse() {
        final Invocation invocation = mock(RpcInvocation.class);
        final CompletableFuture<AppResponse> future = new CompletableFuture<>();
        final AsyncRpcResult asyncResult = new AsyncRpcResult(future, invocation);
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service"));
        when(invoker.invoke(invocation)).thenReturn(asyncResult);
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(new DubboProperties());
        final DubboErrorConsumerFilter consumer = new DubboErrorConsumerFilter();
        consumer.setDubboProperties(new DubboProperties());
        provider.invoke(invoker, invocation);
        consumer.invoke(invoker, invocation);

        future.complete(new AppResponse(new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "internal")
                .withVisibility(ErrorVisibility.INTERNAL)));

        final AppResponse response = asyncResult.getResponseFuture().join();
        assertThat(response.getException()).isInstanceOfSatisfying(ErrorCodeException.class, exception -> {
            assertThat(exception.getErrorCode()).isInstanceOfSatisfying(ErrorCodeCarrier.class, carrier -> {
                assertThat(carrier.message()).isEqualTo("internal");
                assertThat(carrier.visibility()).isEqualTo(ErrorVisibility.INTERNAL);
            });
        });
    }

    /** 延迟完成的普通异常结果不添加错误码协议。 */
    @Test
    void providerPreservesDeferredNativeException() {
        final Invocation invocation = mock(RpcInvocation.class);
        final CompletableFuture<AppResponse> future = new CompletableFuture<>();
        final AsyncRpcResult asyncResult = new AsyncRpcResult(future, invocation);
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service"));
        when(invoker.invoke(invocation)).thenReturn(asyncResult);
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(new DubboProperties());
        provider.invoke(invoker, invocation);
        final IllegalArgumentException original = new IllegalArgumentException("native failure");

        future.complete(new AppResponse(original));

        final AppResponse response = asyncResult.getResponseFuture().join();
        assertThat(response.getException()).isSameAs(original);
        assertThat(response.getAttachment(ErrorCodePropagator.CODE)).isNull();
    }

    /** 验证 provider URL 未声明 application 时使用空来源且错误码不添加服务前缀。 */
    @Test
    void providerUsesEmptySourceServiceWhenApplicationIsMissing() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        final Invoker<?> providerInvoker = mock(Invoker.class);
        when(providerInvoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service"));
        when(providerInvoker.invoke(invocation))
                .thenReturn(new AppResponse(new ErrorCodeException(CommonErrors.ILLEGAL_STATE)));
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(properties);

        final Result wireResult = provider.invoke(providerInvoker, invocation);

        assertThat(wireResult.getAttachment(ErrorCodePropagator.CODE)).isEqualTo("COMMON.ILLEGAL_STATE");
        assertThat(wireResult.getAttachment(ErrorCodePropagator.SOURCE_SERVICE)).isEmpty();
    }

    /** 验证同一个 provider Filter 处理不同 application 的 Invoker 时不会混用错误码前缀。 */
    @Test
    void providerKeepsSourceServiceIsolatedAcrossInvokers() {
        final DubboProperties properties = new DubboProperties();
        final Invocation invocation = mock(Invocation.class);
        final Invoker<?> firstInvoker = mock(Invoker.class);
        when(firstInvoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service?application=first-service"));
        when(firstInvoker.invoke(invocation))
                .thenReturn(new AppResponse(new ErrorCodeException(CommonErrors.ILLEGAL_STATE)));
        final Invoker<?> secondInvoker = mock(Invoker.class);
        when(secondInvoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service?application=second-service"));
        when(secondInvoker.invoke(invocation))
                .thenReturn(new AppResponse(new ErrorCodeException(CommonErrors.ILLEGAL_STATE)));
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(properties);

        final Result firstResult = provider.invoke(firstInvoker, invocation);
        final Result secondResult = provider.invoke(secondInvoker, invocation);

        assertThat(firstResult.getAttachment(ErrorCodePropagator.CODE)).isEqualTo("FIRST-SERVICE.COMMON.ILLEGAL_STATE");
        assertThat(firstResult.getAttachment(ErrorCodePropagator.SOURCE_SERVICE))
                .isEqualTo("first-service");
        assertThat(secondResult.getAttachment(ErrorCodePropagator.CODE))
                .isEqualTo("SECOND-SERVICE.COMMON.ILLEGAL_STATE");
        assertThat(secondResult.getAttachment(ErrorCodePropagator.SOURCE_SERVICE))
                .isEqualTo("second-service");
    }

    /** 验证关闭错误传播后 Filter 不修改 Dubbo 原始结果。 */
    @Test
    void disabledErrorPropagationLeavesResultUntouched() {
        final DubboProperties properties = new DubboProperties();
        properties.getError().setEnabled(false);
        final Invocation invocation = mock(Invocation.class);
        final Result original = new AppResponse(new ErrorCodeException(CommonErrors.ILLEGAL_STATE));
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenReturn(original);
        final DubboErrorProviderFilter filter = new DubboErrorProviderFilter();
        filter.setDubboProperties(properties);

        assertThat(filter.invoke(invoker, invocation)).isSameAs(original);
        assertThat(original.getException()).isInstanceOf(ErrorCodeException.class);
    }

    /** 使用真实 provider Filter 生成指定服务的错误传播附件。 */
    private static Result invokeProvider(
            final DubboProperties properties,
            final Invocation invocation,
            final String service,
            final ErrorCodeException exception) {
        final Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://localhost/service?application=" + service));
        when(invoker.invoke(invocation)).thenReturn(new AppResponse(exception));
        final DubboErrorProviderFilter provider = new DubboErrorProviderFilter();
        provider.setDubboProperties(properties);
        return provider.invoke(invoker, invocation);
    }
}
