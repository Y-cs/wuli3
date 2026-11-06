package com.kjs.wuli3.web.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 提供单次 Web 错误识别的结果，供状态定制器直接使用。
 *
 * 注意：error 为显式映射后的异常，未映射时为原始异常；source 为已识别的异常来源，
 * 透明包装时可能与 error 不同。未知异常的 source 为 error，默认状态为 500。
 *
 * @param error 进入默认识别的异常
 * @param source 默认识别选定的异常来源
 * @param code 已识别的业务错误码或协议错误码
 * @param defaultStatus 定制前的默认 HTTP 错误状态
 * @author 国杨 create on 2026/11/5 17:10
 */
public record WebErrorStatusContext(Throwable error, Throwable source, ErrorCode code, HttpStatus defaultStatus) {}
