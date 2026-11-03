package com.kjs.wuli3.web.internal.handler;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.web.internal.error.WebErrorResponseMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * 保存一次错误识别得到的语义来源、错误码及 HTTP 协议结果。
 *
 * 注意：source 是被明确识别的异常，不一定是最外层捕获异常；原始异常由出口单独保留用于诊断。
 * headers 仅包含所选来源的只读白名单协议头。
 * validationDetailsAllowed 由首次识别确定，状态覆盖不能赋予服务端错误公开校验详情的资格。
 *
 * @author 国杨 create on 2026/11/3 11:26
 */
record WebErrorResolution(
        Throwable source, ErrorCode code, HttpStatus status, HttpHeaders headers, boolean validationDetailsAllowed) {

    /** 用已确定的协议状态选择安全默认错误码，同时固定请求校验详情的资格。 */
    static WebErrorResolution protocol(
            final Throwable source,
            final HttpStatus status,
            final HttpHeaders headers,
            final boolean validationDetailsAllowed) {
        return new WebErrorResolution(
                source, WebErrorResponseMapper.responseCode(status), status, headers, validationDetailsAllowed);
    }

    /** 应用自定义状态覆盖；业务码保持不变，协议码跟随状态，原状态的头不跨状态复用。 */
    WebErrorResolution withStatus(final HttpStatus status) {
        if (this.status.isSameCodeAs(status)) {
            return this;
        }
        final ErrorCode code =
                this.source instanceof ErrorCodeException ? this.code : WebErrorResponseMapper.responseCode(status);
        return new WebErrorResolution(this.source, code, status, HttpHeaders.EMPTY, this.validationDetailsAllowed);
    }
}
