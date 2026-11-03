package com.kjs.wuli3.web.internal.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.web.error.WebErrors;
import com.kjs.wuli3.web.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Web 错误处理过程共享的响应映射规则。
 *
 * <p>core 统一完成可见性过滤；Web 只投影展示字段，不输出传播模型的诊断信息。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class WebErrorResponseMapper {

    private static final String REQUEST_ID = "requestId";
    private static final String CODE = "code";
    /** 仅初始化一次，并按数值兼容 Spring 历史状态别名；请求处理无需扫描枚举。 */
    private static final Map<Integer, WebErrors> STATUS_CODES = WebErrorResponseMapper.statusCodes();

    private WebErrorResponseMapper() {}

    /** 保持可表达的错误状态；未知 4xx 回退为 400，其他未知或非错误状态回退为 500。 */
    public static HttpStatus normalize(final int statusCode) {
        final HttpStatus status = HttpStatus.resolve(statusCode);
        if (status != null && status.isError()) {
            return status;
        }
        return statusCode >= 400 && statusCode < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /** 返回已知协议状态的专用错误码，未覆盖的状态按调用方或服务端类别兜底。 */
    public static ErrorCode responseCode(final HttpStatus status) {
        final WebErrors code = WebErrorResponseMapper.STATUS_CODES.get(status.value());
        if (code != null) {
            return code;
        }
        if (status.is5xxServerError()) {
            return WebErrors.INTERNAL_ERROR;
        }
        return WebErrors.BAD_REQUEST;
    }

    public static String message(final Throwable ex) {
        final String message = ex.getMessage();
        return message == null ? WebErrors.INTERNAL_ERROR.getMessage() : message;
    }

    /** 使用已经执行可见性处理的响应数据创建原生 ProblemDetail。 */
    public static ResponseEntity<ProblemDetail> nativeError(final HttpStatus status, final ApiResponse<?> response) {
        final ProblemDetail problemDetail = WebErrorResponseMapper.problemDetail(status);
        problemDetail.setDetail(response.message());
        problemDetail.setProperty(CODE, response.code());
        problemDetail.setProperty(REQUEST_ID, response.requestId());
        return ResponseEntity.status(status).body(problemDetail);
    }

    public static ResponseEntity<ProblemDetail> nativeProblemDetail(
            final int statusCode, final String detail, final @Nullable String requestId) {
        final HttpStatus status = HttpStatus.valueOf(statusCode);
        final ProblemDetail problemDetail = WebErrorResponseMapper.problemDetail(status);
        problemDetail.setDetail(detail);
        problemDetail.setProperty(REQUEST_ID, requestId);
        return ResponseEntity.status(status).body(problemDetail);
    }

    /** 从错误码的唯一状态声明构建不可变反向索引。 */
    private static Map<Integer, WebErrors> statusCodes() {
        final Map<Integer, WebErrors> codes = new HashMap<>();
        for (final WebErrors error : WebErrors.values()) {
            final HttpStatus status = error.getHttpStatus();
            if (status != null) {
                codes.put(status.value(), error);
            }
        }
        return Map.copyOf(codes);
    }

    /** 创建安全的协议错误正文，并修正旧版 Spring 对 421 的历史标题。 */
    private static ProblemDetail problemDetail(final HttpStatus status) {
        final ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        if (status == WebErrors.MISDIRECTED_REQUEST.getHttpStatus()) {
            problemDetail.setTitle("Misdirected Request");
        }
        return problemDetail;
    }
}
