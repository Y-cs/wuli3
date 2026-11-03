package com.kjs.wuli3.core.error.resolver;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.builtin.SystemErrors;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import java.util.Locale;
import java.util.Objects;

/**
 * 统一解析稳定错误码，并生成服务边界可传播的错误值。
 *
 * @author GuoYang create on 2026/9/24 10:00
 */
public final class ErrorResolver {

    private final String serviceCode;
    private final String rawServiceCode;

    /** 创建使用指定服务标识的错误解析器。 */
    public ErrorResolver(final String serviceCode) {
        final String actualServiceCode =
                Objects.requireNonNull(serviceCode, "serviceCode").trim();
        this.rawServiceCode = actualServiceCode;
        this.serviceCode = actualServiceCode.toUpperCase(Locale.ROOT);
    }

    /** 将错误标识格式化为稳定字符串错误码；服务边界输出使用 {@link #resolveBoundary(ErrorCodeException)}。 */
    public String resolveCode(final ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode");
        if (errorCode instanceof ErrorCodeCarrier carrier) {
            return carrier.code();
        }
        if (!(errorCode instanceof Enum<?> errorEnum)) {
            throw new ErrorCodeException(com.kjs.wuli3.core.error.builtin.ErrorFrameworkErrors.INVALID_ERROR_CODE);
        }
        final ErrorModule module = ErrorMetadataResolver.instance().getErrorModule(errorCode);
        final String moduleName = module.name().trim();
        if (moduleName.isBlank()) {
            throw new ErrorCodeException(com.kjs.wuli3.core.error.builtin.ErrorFrameworkErrors.MODULE_NOT_FOUND);
        }
        final String prefix = this.serviceCode.isBlank() ? "" : this.serviceCode + ".";
        return (prefix + moduleName + "." + errorEnum.name()).toUpperCase(Locale.ROOT);
    }

    /** 生成可信内部传播值，保留错误信息及最终对外可见性，不提前脱敏。 */
    public ErrorCodeCarrier resolvePropagation(final ErrorCodeException exception) {
        final ErrorCodeException actualException = Objects.requireNonNull(exception, "exception");
        final ErrorCodeCarrier remote = actualException.asRemoteError().orElse(null);
        final String currentCode = this.resolveCode(actualException.getErrorCode());
        return new ErrorCodeCarrier(
                remote == null ? currentCode : remote.originalCode(),
                currentCode,
                Objects.requireNonNullElse(
                        actualException.getMessage(),
                        actualException.getErrorCode().getMessage()),
                actualException.getOrigin(),
                actualException.getSeverity(),
                remote == null ? this.rawServiceCode : remote.sourceService(),
                actualException.getVisibility());
    }

    /**
     * 在最终公开边界应用可见性；占位码和消息按当前责任归属选择。
     *
     * <p>返回值中的原始诊断字段仅供内部使用，不应直接序列化到公开响应。
     */
    public ErrorCodeCarrier resolveBoundary(final ErrorCodeException exception) {
        return this.boundary(this.resolvePropagation(exception));
    }

    /**
     * 为错误值生成可信传播模型，无需为协议错误创建异常堆栈。
     *
     * <p>远程错误的原始身份与来源保留，消息使用当前显式提供的值。
     *
     * @throws ErrorCodeException 错误码或消息为 null 时返回统一参数错误
     */
    public ErrorCodeCarrier resolvePropagation(final ErrorCode errorCode, final String message) {
        Asserts.whenNull(errorCode).throwIllegalArgumentException("errorCode must not be null");
        Asserts.whenNull(message).throwIllegalArgumentException("message must not be null");
        final String code = this.resolveCode(errorCode);
        final ErrorCodeCarrier remote = errorCode instanceof ErrorCodeCarrier carrier ? carrier : null;
        final ErrorMetadataResolver metadata = ErrorMetadataResolver.instance();
        return new ErrorCodeCarrier(
                remote == null ? code : remote.originalCode(),
                code,
                message,
                metadata.getOrigin(errorCode),
                metadata.getSeverity(errorCode),
                remote == null ? this.rawServiceCode : remote.sourceService(),
                metadata.getVisibility(errorCode));
    }

    /**
     * 将错误值直接投影到公开边界，无需为协议错误创建异常堆栈。
     *
     * <p>使用错误码自身元数据；需要运行时责任或可见性覆盖时仍使用异常入口。
     * 远程错误的原始身份与来源保留，消息使用当前边界显式提供的值。
     *
     * @throws ErrorCodeException 错误码或消息为 null 时返回统一参数错误
     */
    public ErrorCodeCarrier resolveBoundary(final ErrorCode errorCode, final String message) {
        return this.boundary(this.resolvePropagation(errorCode, message));
    }

    /** 两个输入入口共用唯一的公开投影规则，原诊断信息仍保留在内部载体中。 */
    private ErrorCodeCarrier boundary(final ErrorCodeCarrier propagation) {
        final ErrorCode fallback = propagation.origin() == ErrorOrigin.CALLER
                ? CommonErrors.REQUEST_REJECTED
                : SystemErrors.INTERNAL_ERROR;
        final ErrorVisibility visibility = propagation.visibility();
        final String visibleCode =
                switch (visibility) {
                    case PUBLIC, CODE_ONLY -> propagation.code();
                    case MESSAGE_ONLY, INTERNAL -> this.resolveCode(fallback);
                };
        final String visibleMessage =
                switch (visibility) {
                    case PUBLIC, MESSAGE_ONLY -> propagation.message();
                    case CODE_ONLY, INTERNAL -> fallback.getMessage();
                };
        return new ErrorCodeCarrier(
                propagation.originalCode(),
                visibleCode,
                visibleMessage,
                propagation.origin(),
                propagation.severity(),
                propagation.sourceService(),
                visibility);
    }
}
