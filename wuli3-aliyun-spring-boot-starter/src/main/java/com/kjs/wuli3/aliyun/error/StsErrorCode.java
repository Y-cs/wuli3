package com.kjs.wuli3.aliyun.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorMetadata;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * STS 模块错误码。
 *
 * @author lipt create on 2026/11/9 12:00
 */
@ErrorModule(
        name = "STS",
        defaultMetadata = @ErrorMetadata(origin = ErrorOrigin.CALLER, severity = ErrorSeverity.WARNING))
@RequiredArgsConstructor
public enum StsErrorCode implements ErrorCode {
    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.CRITICAL)
    OPERATE_ERROR("STS 操作失败"),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.CRITICAL)
    INVALID_RESPONSE("STS 响应缺少必要字段"),

    INVALID_CONFIG("STS 配置无效");

    @Getter
    private final String message;
}
