package com.kjs.wuli3.web.internal.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.web.error.WebErrors;
import jakarta.validation.ConstraintViolationException;
import java.io.Serial;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 验证默认 HTTP 状态解析的优先级和异常链边界。
 *
 * @author 国杨 create on 2026/11/3 10:21
 */
class WebErrorResolverTest {

    /** 内建调用方错误保留各自的 HTTP 语义。 */
    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, BAD_REQUEST",
        "UNAUTHORIZED, UNAUTHORIZED",
        "FORBIDDEN, FORBIDDEN",
        "NOT_FOUND, NOT_FOUND",
        "PAYLOAD_TOO_LARGE, PAYLOAD_TOO_LARGE"
    })
    void preservesCallerWebStatuses(final WebErrors code, final HttpStatus expected) {
        assertThat(WebErrorResolver.resolve(new ErrorCodeException(code)).status())
                .isEqualTo(expected);
        assertThat(WebErrorResolver.resolve(new CompletionException(new ErrorCodeException(code)))
                        .status())
                .isEqualTo(expected);
    }

    /** 运行时服务端责任覆盖所有调用方状态，包括认证与授权错误。 */
    @ParameterizedTest
    @EnumSource(
            value = WebErrors.class,
            names = {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "PAYLOAD_TOO_LARGE"})
    void serverOriginTakesPriority(final WebErrors code) {
        assertThat(WebErrorResolver.resolve(new ErrorCodeException(code).withOrigin(ErrorOrigin.SERVER))
                        .status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 业务异常自身的责任与错误码优先于底层协议异常。 */
    @Test
    void businessErrorOverridesCause() {
        final ErrorCodeException error = new ErrorCodeException(
                CommonErrors.ILLEGAL_ARGUMENT, new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(WebErrorResolver.resolve(error).status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(WebErrorResolver.resolve(error.withOrigin(ErrorOrigin.SERVER))
                        .status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 远程错误只使用传播责任，不根据同名字符串猜测 HTTP 状态。 */
    @Test
    void remoteErrorsKeepOriginWithoutGuessingStatus() {
        final ErrorResolver errorResolver = new ErrorResolver("REMOTE");
        final var caller = errorResolver.resolvePropagation(new ErrorCodeException(WebErrors.NOT_FOUND));
        assertThat(WebErrorResolver.resolve(new ErrorCodeException(caller)).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        final var server = errorResolver.resolvePropagation(
                new ErrorCodeException(WebErrors.UNAUTHORIZED).withOrigin(ErrorOrigin.SERVER));
        assertThat(WebErrorResolver.resolve(new ErrorCodeException(server)).status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 框架声明的状态保留其协议语义。 */
    @Test
    void preservesFrameworkStatus() {
        assertThat(WebErrorResolver.resolve(new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE))
                        .status())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** HttpStatus 无法表达的错误码按状态类别回退，非错误状态拒绝作为错误响应。 */
    @ParameterizedTest
    @CsvSource({
        "499, BAD_REQUEST",
        "599, INTERNAL_SERVER_ERROR",
        "200, INTERNAL_SERVER_ERROR",
        "302, INTERNAL_SERVER_ERROR"
    })
    void normalizesUnsupportedFrameworkStatus(final int status, final HttpStatus expected) {
        assertThat(WebErrorResolver.resolve(new ErrorResponseException(HttpStatusCode.valueOf(status)))
                        .status())
                .isEqualTo(expected);
    }

    /** 标准错误状态保持不变，包括未单独列举的协议状态。 */
    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            names = {"NOT_ACCEPTABLE", "TOO_MANY_REQUESTS", "SERVICE_UNAVAILABLE"})
    void preservesStandardFrameworkStatus(final HttpStatus status) {
        assertThat(WebErrorResolver.resolve(new ErrorResponseException(status)).status())
                .isEqualTo(status);
    }

    /** 支持 ResponseStatus 的别名、继承、组合注解与透明包装原因。 */
    @Test
    void honorsResponseStatusAnnotations() {
        for (final Throwable error : new Throwable[] {
            new MissingResourceException(),
            new InheritedMissingResourceException(),
            new ComposedMissingResourceException(),
            new CompletionException(new MissingResourceException())
        }) {
            assertThat(WebErrorResolver.resolve(error).status()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    /** 外层声明的状态优先于更深的注解，普通内层参数异常不会误降级为四百。 */
    @Test
    void outerStatusWinsAndUnknownWrappersStayServerErrors() {
        assertThat(WebErrorResolver.resolve(new ErrorResponseException(
                                HttpStatus.SERVICE_UNAVAILABLE, new MissingResourceException()))
                        .status())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(WebErrorResolver.resolve(new RuntimeException(new IllegalArgumentException()))
                        .status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(WebErrorResolver.resolve(new IllegalArgumentException()).status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 只有明确的异步、反射包装可穿透，普通包装不隐式继承内部协议或业务语义。 */
    @Test
    void unwrapsOnlyTransparentWrappers() {
        final ErrorCodeException business = new ErrorCodeException(WebErrors.NOT_FOUND);
        assertThat(WebErrorResolver.resolve(new CompletionException(
                                new ExecutionException(new InvocationTargetException(business))))
                        .status())
                .isEqualTo(HttpStatus.NOT_FOUND);
        for (final Throwable cause : new Throwable[] {
            business, new ErrorResponseException(HttpStatus.UNAUTHORIZED), new MissingResourceException()
        }) {
            assertThat(WebErrorResolver.resolve(new RuntimeException(cause)).status())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        assertThat(WebErrorResolver.resolve(new OpaqueException()).status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 循环 cause 链不能阻塞异常出口。 */
    @Test
    void terminatesOnCyclicCauseChain() {
        final CompletionException first = new TransparentException();
        final CompletionException second = new TransparentException();
        first.initCause(second);
        second.initCause(first);
        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThat(WebErrorResolver.resolve(first).status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** 空校验集合没有调用方错误的依据，应按未知服务端异常处理。 */
    @Test
    void emptyViolationsAreNotCallerErrors() {
        assertThat(WebErrorResolver.resolve(new ConstraintViolationException(Set.of()))
                        .status())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 模拟可形成循环的透明包装。 */
    private static class TransparentException extends CompletionException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** 普通异常的 cause 不能被默认解析过程访问。 */
    private static class OpaqueException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Throwable getCause() {
            throw new AssertionError("不应检查普通异常的内部原因");
        }
    }

    /** 声明 value 别名和内部原因文本，原因文本不影响 HTTP 语义。 */
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "secret reason")
    private static class MissingResourceException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** 验证异常子类继承父类的状态声明。 */
    private static class InheritedMissingResourceException extends MissingResourceException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** 声明组合状态注解。 */
    @Retention(RetentionPolicy.RUNTIME)
    @ResponseStatus(code = HttpStatus.NOT_FOUND)
    private @interface MissingResource {}

    /** 验证组合注解的状态声明。 */
    @MissingResource
    private static class ComposedMissingResourceException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
