package com.kjs.wuli3.core.error.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.builtin.SystemErrors;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import org.junit.jupiter.api.Test;

/** 验证错误码解析和错误可见性传播规则。 */
class ErrorResolverTest {

    private final ErrorResolver resolver = new ErrorResolver("group");

    /** 无异常输入与原异常入口输出一致，服务前缀及本地元数据不能丢失。 */
    @Test
    void errorValuesMatchExceptionProjection() {
        for (final ErrorCode code :
                new ErrorCode[] {CommonErrors.ILLEGAL_ARGUMENT, SystemErrors.CONFIGURATION_MISSING}) {
            assertThat(this.resolver.resolveBoundary(code, "override"))
                    .isEqualTo(this.resolver.resolveBoundary(new ErrorCodeException(code, "override")));
        }
    }

    /** 远程错误值保留原身份、来源和全部策略，消息覆盖后仍执行同一可见性过滤。 */
    @Test
    void remoteErrorValuesMatchExceptionProjection() {
        for (final ErrorVisibility visibility : ErrorVisibility.values()) {
            for (final ErrorOrigin origin : ErrorOrigin.values()) {
                final ErrorCodeCarrier remote = new ErrorCodeCarrier(
                        "REMOTE.ORIGINAL.FAILURE",
                        "REMOTE.PUBLIC.FAILURE",
                        "remote message",
                        origin,
                        ErrorSeverity.CRITICAL,
                        "remote-service",
                        visibility);
                assertThat(this.resolver.resolveBoundary(remote, "override"))
                        .isEqualTo(this.resolver.resolveBoundary(new ErrorCodeException(remote, "override")));
            }
        }
    }

    /** 错误值入口的非法输入进入统一参数错误链。 */
    @Test
    @SuppressWarnings("NullAway") // 刻意违反非空契约，验证公开入口的运行时校验。
    void rejectsNullErrorValues() {
        assertThatThrownBy(() -> this.resolver.resolveBoundary(null, "message"))
                .isInstanceOfSatisfying(
                        ErrorCodeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrors.ILLEGAL_ARGUMENT));
        assertThatThrownBy(() -> this.resolver.resolveBoundary(CommonErrors.ILLEGAL_ARGUMENT, null))
                .isInstanceOfSatisfying(
                        ErrorCodeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrors.ILLEGAL_ARGUMENT));
    }

    @Test
    void appliesVisibilityPolicies() {
        for (final ErrorVisibility visibility : ErrorVisibility.values()) {
            final ErrorCodeCarrier carrier = this.resolver.resolveBoundary(
                    new ErrorCodeException(SystemErrors.CONFIGURATION_MISSING).withVisibility(visibility));
            if (visibility == ErrorVisibility.PUBLIC) {
                assertThat(carrier.code()).isEqualTo("GROUP.SYSTEM.CONFIGURATION_MISSING");
                assertThat(carrier.message()).isEqualTo(SystemErrors.CONFIGURATION_MISSING.getMessage());
            } else if (visibility == ErrorVisibility.CODE_ONLY) {
                assertThat(carrier.code()).isEqualTo("GROUP.SYSTEM.CONFIGURATION_MISSING");
                assertThat(carrier.message()).isEqualTo(SystemErrors.INTERNAL_ERROR.getMessage());
            } else if (visibility == ErrorVisibility.MESSAGE_ONLY) {
                assertThat(carrier.code()).isEqualTo("GROUP.SYSTEM.INTERNAL_ERROR");
                assertThat(carrier.message()).isEqualTo(SystemErrors.CONFIGURATION_MISSING.getMessage());
            } else {
                assertThat(carrier.code()).isEqualTo("GROUP.SYSTEM.INTERNAL_ERROR");
                assertThat(carrier.message()).isEqualTo(SystemErrors.INTERNAL_ERROR.getMessage());
            }
            assertThat(carrier.sourceService()).isEqualTo("group");
        }
    }

    @Test
    void preservesTrustedPropagationUntilPublicBoundary() {
        for (final ErrorVisibility visibility : ErrorVisibility.values()) {
            final ErrorCodeCarrier propagated = this.resolver.resolvePropagation(
                    new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "敏感详情").withVisibility(visibility));
            assertThat(propagated.code()).isEqualTo("GROUP.COMMON.ILLEGAL_ARGUMENT");
            assertThat(propagated.message()).isEqualTo("敏感详情");
            assertThat(propagated.visibility()).isEqualTo(visibility);
            final ErrorCodeException received = new ErrorCodeException(propagated);
            assertThat(received.getVisibility()).isEqualTo(visibility);
            final ErrorCodeCarrier output = this.resolver.resolveBoundary(received);
            assertThat(output.code())
                    .isEqualTo(
                            visibility == ErrorVisibility.PUBLIC || visibility == ErrorVisibility.CODE_ONLY
                                    ? "GROUP.COMMON.ILLEGAL_ARGUMENT"
                                    : "GROUP.COMMON.REQUEST_REJECTED");
            assertThat(output.message())
                    .isEqualTo(
                            visibility == ErrorVisibility.PUBLIC || visibility == ErrorVisibility.MESSAGE_ONLY
                                    ? "敏感详情"
                                    : "请求未被接受");
        }
    }

    @Test
    void localOriginAndVisibilityOverridesTakePriorityOverRemoteMetadata() {
        final ErrorCodeCarrier remote = this.resolver.resolvePropagation(
                new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT).withVisibility(ErrorVisibility.INTERNAL));
        final ErrorCodeException received = new ErrorCodeException(remote).withOrigin(ErrorOrigin.SERVER);
        assertThat(this.resolver.resolvePropagation(received).origin()).isEqualTo(ErrorOrigin.SERVER);
        assertThat(this.resolver.resolveBoundary(received).code()).isEqualTo("GROUP.SYSTEM.INTERNAL_ERROR");
        received.withVisibility(ErrorVisibility.PUBLIC);
        assertThat(this.resolver.resolveBoundary(received).code()).isEqualTo(remote.code());
    }

    @Test
    void preservesRemoteOriginalIdentityAndNeverRestoresHiddenCode() {
        final ErrorCodeCarrier remote = new ErrorCodeCarrier(
                "ORDER.SECRET.FAILURE",
                "GROUP.SYSTEM.INTERNAL_ERROR",
                "内部错误",
                ErrorOrigin.SERVER,
                ErrorSeverity.CRITICAL,
                "order");
        final ErrorCodeCarrier forwarded =
                this.resolver.resolveBoundary(new ErrorCodeException(remote).withVisibility(ErrorVisibility.PUBLIC));

        assertThat(forwarded.originalCode()).isEqualTo(remote.originalCode());
        assertThat(forwarded.code()).isEqualTo(remote.code());
        assertThat(forwarded.getName()).isEqualTo("FAILURE");
        assertThat(forwarded.sourceService()).isEqualTo("order");
    }
}
