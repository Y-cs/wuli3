package com.kjs.wuli3.web.internal.error;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.web.internal.advice.ApiResponseFactory;
import com.kjs.wuli3.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 处理容器转交的 ERROR dispatch，并复用 Web 的统一错误投影。
 *
 * 注意：未匹配 Controller 的请求没有方法级原生响应注解，因此始终使用应用级 ApiResponse 格式。
 * 容器提供的异常消息、堆栈和扩展属性仅用于内部诊断，不直接返回给调用方。
 * 响应已提交时容器不会再次调用本控制器，诊断由容器日志负责。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
@RequestMapping("${server.error.path:${error.path:/error}}")
@Controller
@RequiredArgsConstructor
public final class WebErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final ApiResponseFactory responseFactory;

    /** 返回容器转交错误的统一应用级响应。 */
    @RequestMapping
    public ResponseEntity<ApiResponse<Object>> error(
            final HttpServletRequest request, final HttpServletResponse response) {
        final ServletWebRequest webRequest = new ServletWebRequest(request, response);
        final Map<String, Object> attributes =
                this.errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
        final HttpStatus status = WebErrorController.status(attributes, response.getStatus());
        final ErrorCode responseCode = WebErrorResponseMapper.responseCode(status);
        final ErrorCodeException semanticError = new ErrorCodeException(responseCode, responseCode.getMessage());
        final ApiResponse<Object> body = this.responseFactory.fail(semanticError);
        response.setStatus(status.value());
        return ResponseEntity.status(status).body(body);
    }

    private static HttpStatus status(final Map<String, Object> attributes, final int responseStatus) {
        final Object status = attributes.get("status");
        if (status instanceof Number number) {
            try {
                return HttpStatus.valueOf(number.intValue());
            } catch (final IllegalArgumentException ignored) {
                // 使用容器状态或内部错误兜底，不暴露非法状态值。
            }
        }
        try {
            return HttpStatus.valueOf(responseStatus);
        } catch (final IllegalArgumentException ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
