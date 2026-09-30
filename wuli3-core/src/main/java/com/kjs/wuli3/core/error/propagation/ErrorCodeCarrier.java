package com.kjs.wuli3.core.error.propagation;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import java.util.Objects;

/**
 * 跨服务边界传输的错误值，同时也是接收方可携带的 {@link ErrorCode}。
 *
 * @param originalCode 原始错误码，用于诊断身份识别
 * @param code 稳定错误码；可信内部传播不做脱敏
 * @param message 错误消息；公开边界输出时才应用可见性
 * @param origin 错误责任归属
 * @param severity 错误严重程度
 * @param sourceService 错误来源服务
 * @param visibility 最终对外输出的可见性策略，不限制可信内部传播
 * @author GuoYang create on 2026/8/28 20:00
 */
public record ErrorCodeCarrier(
        String originalCode,
        String code,
        String message,
        ErrorOrigin origin,
        ErrorSeverity severity,
        String sourceService,
        ErrorVisibility visibility)
        implements ErrorCode {

    /** 兼容旧协议已完成投影的错误值，不推断已丢失的可见性策略。 */
    public ErrorCodeCarrier(
            final String originalCode,
            final String code,
            final String message,
            final ErrorOrigin origin,
            final ErrorSeverity severity,
            final String sourceService) {
        this(originalCode, code, message, origin, severity, sourceService, ErrorVisibility.PUBLIC);
    }

    /** 校验传输值字段完整且错误码非空。 */
    public ErrorCodeCarrier {
        Objects.requireNonNull(originalCode, "originalCode");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(sourceService, "sourceService");
        Objects.requireNonNull(visibility, "visibility");
        if (originalCode.isBlank()) {
            throw new IllegalArgumentException("originalCode must not be blank");
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    /** 返回按边界处理后的错误消息。 */
    @Override
    public String getMessage() {
        return this.message;
    }

    /** 从完整错误码提取末段错误名称。 */
    @Override
    public String getName() {
        final int lastDot = this.originalCode.lastIndexOf('.');
        return lastDot < 0 ? this.originalCode : this.originalCode.substring(lastDot + 1);
    }

    /** 返回传播协议类型，避免接收方尝试加载提供方业务枚举。 */
    @Override
    public Class<? extends ErrorCode> getErrorType() {
        return ErrorCodeCarrier.class;
    }

    /** 判断错误码是否为跨服务接收的错误。 */
    public static boolean isRemoteError(final ErrorCode errorCode) {
        return errorCode instanceof ErrorCodeCarrier;
    }
}
