package com.kjs.wuli3.web.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorMetadata;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Web starter 定义的错误码及已知有效 HTTP 4xx、5xx 协议状态。
 *
 * <p>协议错误的状态在此统一声明，供异常解析与响应投影双向使用；不为未分配、未使用或已废止状态新增专用错误码。
 * 默认责任与状态系列一致，运行时覆盖责任后，由状态解析器按新责任回退为 400 或 500。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@Getter
@RequiredArgsConstructor
@ErrorModule(
        name = "WEB",
        defaultMetadata = @ErrorMetadata(origin = ErrorOrigin.CALLER, severity = ErrorSeverity.NORMAL))
public enum WebErrors implements ErrorCode {
    SUCCESS("成功", null),
    BAD_REQUEST("请求参数错误", HttpStatus.BAD_REQUEST),

    @ErrorMetadata(severity = ErrorSeverity.WARNING)
    UNAUTHORIZED("未认证", HttpStatus.UNAUTHORIZED),

    PAYMENT_REQUIRED("需要付费", HttpStatus.PAYMENT_REQUIRED),

    @ErrorMetadata(severity = ErrorSeverity.WARNING)
    FORBIDDEN("无权限", HttpStatus.FORBIDDEN),

    NOT_FOUND("资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("无法提供可接受的响应格式", HttpStatus.NOT_ACCEPTABLE),
    PROXY_AUTHENTICATION_REQUIRED("需要代理认证", HttpStatus.PROXY_AUTHENTICATION_REQUIRED),
    REQUEST_TIMEOUT("请求超时", HttpStatus.REQUEST_TIMEOUT),
    CONFLICT("请求与资源当前状态冲突", HttpStatus.CONFLICT),
    GONE("资源已不可用", HttpStatus.GONE),
    LENGTH_REQUIRED("缺少请求内容长度", HttpStatus.LENGTH_REQUIRED),
    PRECONDITION_FAILED("请求前置条件不满足", HttpStatus.PRECONDITION_FAILED),
    PAYLOAD_TOO_LARGE("请求体过大", HttpStatus.PAYLOAD_TOO_LARGE),
    URI_TOO_LONG("请求地址过长", HttpStatus.URI_TOO_LONG),
    UNSUPPORTED_MEDIA_TYPE("请求媒体类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    REQUESTED_RANGE_NOT_SATISFIABLE("请求范围无法满足", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE),
    EXPECTATION_FAILED("请求预期条件无法满足", HttpStatus.EXPECTATION_FAILED),
    // Spring 6.2 的 421 仍使用历史名称；按数值保留当前协议语义，避免依赖已废弃的枚举常量。
    MISDIRECTED_REQUEST("请求被发送到无法处理的服务器", HttpStatus.valueOf(421)),
    UNPROCESSABLE_ENTITY("请求内容无法处理", HttpStatus.UNPROCESSABLE_ENTITY),
    LOCKED("资源已锁定", HttpStatus.LOCKED),
    FAILED_DEPENDENCY("依赖请求失败", HttpStatus.FAILED_DEPENDENCY),
    TOO_EARLY("请求过早，存在重放风险", HttpStatus.TOO_EARLY),
    UPGRADE_REQUIRED("需要升级请求协议", HttpStatus.UPGRADE_REQUIRED),
    PRECONDITION_REQUIRED("缺少请求前置条件", HttpStatus.PRECONDITION_REQUIRED),
    TOO_MANY_REQUESTS("请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    REQUEST_HEADER_FIELDS_TOO_LARGE("请求头过大", HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE),
    UNAVAILABLE_FOR_LEGAL_REASONS("因法律原因无法提供资源", HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    INTERNAL_ERROR("内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    NOT_IMPLEMENTED("服务不支持所请求的功能", HttpStatus.NOT_IMPLEMENTED),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    BAD_GATEWAY("上游服务响应无效", HttpStatus.BAD_GATEWAY),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    SERVICE_UNAVAILABLE("服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    GATEWAY_TIMEOUT("上游服务响应超时", HttpStatus.GATEWAY_TIMEOUT),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    HTTP_VERSION_NOT_SUPPORTED("服务不支持所使用的 HTTP 版本", HttpStatus.HTTP_VERSION_NOT_SUPPORTED),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    VARIANT_ALSO_NEGOTIATES("服务内容协商配置错误", HttpStatus.VARIANT_ALSO_NEGOTIATES),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    INSUFFICIENT_STORAGE("服务存储空间不足", HttpStatus.INSUFFICIENT_STORAGE),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    LOOP_DETECTED("服务处理检测到循环", HttpStatus.LOOP_DETECTED),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    NETWORK_AUTHENTICATION_REQUIRED("需要网络认证", HttpStatus.NETWORK_AUTHENTICATION_REQUIRED),

    @ErrorMetadata(origin = ErrorOrigin.SERVER, severity = ErrorSeverity.WARNING)
    SERVICE_CODE_NOT_FOUND("服务编码未设置", null);

    private final String message;
    private final @Nullable HttpStatus httpStatus;

    /**
     * 返回本地协议错误对应的 HTTP 状态。
     *
     * @return 非协议专用错误返回 null，交由状态解析器按责任处理；运行时责任覆盖仍优先于此状态
     */
    public @Nullable HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
