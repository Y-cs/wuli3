package com.kjs.wuli3.openfeign.service.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorMetadata;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 内部 HTTP 协议失败的稳定分类，未知故障不携带原始异常消息。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@Getter
@RequiredArgsConstructor
@ErrorModule(name = "HTTP_RPC", defaultMetadata = @ErrorMetadata(origin = ErrorOrigin.CALLER))
enum HttpRpcErrors implements ErrorCode {
    UNAUTHENTICATED("内部调用未通过认证"),
    NOT_FOUND("内部调用接口不存在"),
    METHOD_NOT_ALLOWED("内部调用方法不支持"),
    UNSUPPORTED_MEDIA_TYPE("内部调用媒体类型不支持"),
    INVALID_REQUEST("内部调用参数不合法"),
    @ErrorMetadata(
            origin = ErrorOrigin.SERVER,
            severity = ErrorSeverity.CRITICAL,
            visibility = ErrorVisibility.INTERNAL)
    INTERNAL_ERROR("内部调用失败");

    private final String message;
}
