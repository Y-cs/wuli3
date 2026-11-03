package com.kjs.wuli3.web.internal.handler;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.web.error.WebErrorStatusResolver;
import com.kjs.wuli3.web.error.WebErrors;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Spring MVC 与 wuli 错误的默认 HTTP 状态映射。
 *
 * <p>从外到内优先识别业务责任、框架协议、状态注解与安全异常；外层已声明的语义不会被内层覆盖。
 * 业务错误以 {@link ErrorCodeException#getOrigin()} 为准，保留与责任一致的已知 Web 协议状态，其他错误按责任回退为 400 或 500。
 * 未声明状态时才使用已知 Web 错误码回退，并仅按顶层异常识别参数错误；未知异常返回 500。
 *
 * <p>为兼容返回 {@link HttpStatus} 的扩展接口，非标准 4xx 回退为 400，其他非标准或非错误状态回退为 500。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class DefaultWebErrorStatusResolver implements WebErrorStatusResolver {

    /**
     * 按明确错误语义、候选错误码、顶层参数分类的顺序解析状态。
     *
     * <p>候选码不是强制覆盖值：例如处理器传入 INTERNAL_ERROR 占位时，框架声明的 404、405 等状态仍须保留。
     * 只有这些依据均不适用时才返回 500，避免把未知内部故障归为调用方错误。
     *
     * @param error 当前出口捕获的异常，允许包含包装原因
     * @param responseCode 尚未确定 HTTP 状态时使用的候选错误码
     * @return 标准的 4xx 或 5xx 错误状态
     */
    @Override
    public HttpStatus resolve(final Throwable error, final ErrorCode responseCode) {
        final HttpStatus declaredStatus = DefaultWebErrorStatusResolver.declaredStatus(error);
        if (declaredStatus != null) {
            return declaredStatus;
        }
        final HttpStatus fallbackStatus = DefaultWebErrorStatusResolver.webStatus(responseCode);
        if (fallbackStatus != null) {
            return fallbackStatus;
        }
        if (DefaultWebErrorStatusResolver.badRequest(error)) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /** 仅将顶层参数异常视为调用方错误，避免从任意内部原因推断请求错误。 */
    private static boolean badRequest(final Throwable error) {
        if (error instanceof ConstraintViolationException violation) {
            // 返回值违反约束属于服务端契约失败，不能因使用同一校验异常而判为客户端错误。
            // 空集合不能证明存在输入错误；否则 noneMatch 的空集合结果会将未知故障误判为 400。
            return !violation.getConstraintViolations().isEmpty()
                    && violation.getConstraintViolations().stream().noneMatch(item -> {
                        for (final jakarta.validation.Path.Node node : item.getPropertyPath()) {
                            if (node.getKind() == ElementKind.RETURN_VALUE) {
                                return true;
                            }
                        }
                        return false;
                    });
        }
        return error instanceof MethodArgumentNotValidException
                || error instanceof MethodArgumentTypeMismatchException
                || error instanceof ServletRequestBindingException
                || error instanceof HttpMessageNotReadableException
                || error instanceof IllegalArgumentException;
    }

    /**
     * 从外到内识别明确的状态声明，未命中时返回 null 交由调用方继续回退。
     *
     * <p>每层依次检查业务责任、框架协议、状态注解和安全异常，然后才进入下一层。
     * 不能先扫描整条链的某一种异常，否则内层认证失败可能覆盖外层明确的服务端错误。
     * 普通参数异常不在此处展开，避免将内部调用的参数问题误当作 HTTP 请求错误。
     */
    private static @Nullable HttpStatus declaredStatus(final Throwable error) {
        // Throwable 可形成循环，且子类可能自定义相等性；遍历去重必须使用实例身份。
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && visited.add(current)) {
            if (current instanceof ErrorCodeException coded) {
                // getOrigin 同时尊重运行时覆盖和远程元数据，认证等错误名称不能覆盖服务端责任。
                final HttpStatus status = DefaultWebErrorStatusResolver.webStatus(coded.getErrorCode());
                if (coded.getOrigin() == ErrorOrigin.SERVER) {
                    return status != null && status.is5xxServerError() ? status : HttpStatus.INTERNAL_SERVER_ERROR;
                }
                return status != null && status.is4xxClientError() ? status : HttpStatus.BAD_REQUEST;
            }
            if (current instanceof ErrorResponse frameworkError) {
                // Spring 已统一声明 404、405、406、413、415 等协议状态，无需逐个异常类重复映射。
                return DefaultWebErrorStatusResolver.normalize(frameworkError.getStatusCode());
            }
            // 合并查找兼容 value/code 别名、继承和组合注解；reason 可能包含内部信息，不作为公开响应文案。
            final ResponseStatus annotation =
                    AnnotatedElementUtils.findMergedAnnotation(current.getClass(), ResponseStatus.class);
            if (annotation != null) {
                return DefaultWebErrorStatusResolver.normalize(annotation.code());
            }
            final HttpStatus securityStatus = DefaultWebErrorStatusResolver.securityStatus(current);
            if (securityStatus != null) {
                return securityStatus;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 读取本模块明确声明的协议状态，其他错误码返回 null。
     *
     * <p>远程传播值与本地枚举不是同一身份，不能只凭 NOT_FOUND 等名称末段猜测 HTTP 语义；
     * 需要转换时应由开发者显式映射。INTERNAL_ERROR 也可作为尚未归类时的占位码，不能在此强制覆盖状态。
     */
    private static @Nullable HttpStatus webStatus(final ErrorCode code) {
        if (code instanceof WebErrors webError && webError != WebErrors.INTERNAL_ERROR) {
            return webError.getHttpStatus();
        }
        return null;
    }

    /**
     * 将 Spring 的开放状态码模型转换为现有扩展接口要求的 HttpStatus 枚举。
     *
     * <p>HttpStatusCode 允许 499、599 等枚举外状态，直接 valueOf 会使异常处理再次抛异常。
     * 未知 4xx 保留调用方责任并回退为 400；未知 5xx 及误用的 1xx、2xx、3xx 均以 500 兜底。
     */
    private static HttpStatus normalize(final HttpStatusCode status) {
        final HttpStatus standard = HttpStatus.resolve(status.value());
        if (standard != null && standard.isError()) {
            return standard;
        }
        return status.is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 按当前异常的类层级识别可选的 Spring Security 认证与授权异常。
     *
     * <p>Web starter 不强制依赖 Spring Security，因此按完整类名识别并兼容其子类。
     * 此方法只检查当前异常；cause 的遍历统一留给 declaredStatus，以保持外层优先并检测循环。
     */
    private static @Nullable HttpStatus securityStatus(final Throwable error) {
        Class<?> type = error.getClass();
        while (type != null) {
            switch (type.getName()) {
                case "org.springframework.security.core.AuthenticationException" -> {
                    return HttpStatus.UNAUTHORIZED;
                }
                case "org.springframework.security.access.AccessDeniedException" -> {
                    return HttpStatus.FORBIDDEN;
                }
                default -> {}
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
