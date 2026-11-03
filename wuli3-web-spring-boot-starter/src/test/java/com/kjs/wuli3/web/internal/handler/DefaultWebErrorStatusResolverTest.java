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
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 验证默认 HTTP 状态解析的优先级、兼容回退和异常链边界。
 *
 * @author 国杨 create on 2026/11/3 10:21
 */
class DefaultWebErrorStatusResolverTest {

    private final DefaultWebErrorStatusResolver resolver = new DefaultWebErrorStatusResolver();

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
        assertThat(this.resolver.resolve(new ErrorCodeException(code), code)).isEqualTo(expected);
        assertThat(this.resolver.resolve(new RuntimeException(new ErrorCodeException(code)), WebErrors.INTERNAL_ERROR))
                .isEqualTo(expected);
    }

    /** 运行时服务端责任覆盖所有调用方状态，包括认证与授权错误。 */
    @ParameterizedTest
    @EnumSource(
            value = WebErrors.class,
            names = {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "PAYLOAD_TOO_LARGE"})
    void serverOriginTakesPriority(final WebErrors code) {
        assertThat(this.resolver.resolve(new ErrorCodeException(code).withOrigin(ErrorOrigin.SERVER), code))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 未声明 HTTP 协议的异常使用传入的已知 Web 错误码作为回退。 */
    @ParameterizedTest
    @EnumSource(
            value = WebErrors.class,
            names = {"UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "PAYLOAD_TOO_LARGE"})
    void usesResponseCodeAsFallback(final WebErrors code) {
        assertThat(this.resolver.resolve(new RuntimeException(), code)).isEqualTo(HttpStatus.valueOf(code.name()));
    }

    /** 业务异常自身的责任与错误码优先于回退码及底层协议异常。 */
    @Test
    void businessErrorOverridesFallbackAndCause() {
        final ErrorCodeException error = new ErrorCodeException(
                CommonErrors.ILLEGAL_ARGUMENT, new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(this.resolver.resolve(error, WebErrors.NOT_FOUND)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(this.resolver.resolve(error.withOrigin(ErrorOrigin.SERVER), WebErrors.NOT_FOUND))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 远程错误只使用传播责任，不根据同名字符串猜测 HTTP 状态。 */
    @Test
    void remoteErrorsKeepOriginWithoutGuessingStatus() {
        final ErrorResolver errorResolver = new ErrorResolver("REMOTE");
        final var caller = errorResolver.resolvePropagation(new ErrorCodeException(WebErrors.NOT_FOUND));
        assertThat(this.resolver.resolve(new ErrorCodeException(caller), WebErrors.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        final var server = errorResolver.resolvePropagation(
                new ErrorCodeException(WebErrors.UNAUTHORIZED).withOrigin(ErrorOrigin.SERVER));
        assertThat(this.resolver.resolve(new ErrorCodeException(server), WebErrors.UNAUTHORIZED))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 框架声明的状态不会被回退错误码覆盖。 */
    @Test
    void frameworkStatusTakesPriorityOverFallback() {
        assertThat(this.resolver.resolve(
                        new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE), WebErrors.PAYLOAD_TOO_LARGE))
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
        assertThat(this.resolver.resolve(
                        new ErrorResponseException(HttpStatusCode.valueOf(status)), WebErrors.INTERNAL_ERROR))
                .isEqualTo(expected);
    }

    /** 标准错误状态保持不变，包括未单独列举的协议状态。 */
    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            names = {"NOT_ACCEPTABLE", "TOO_MANY_REQUESTS", "SERVICE_UNAVAILABLE"})
    void preservesStandardFrameworkStatus(final HttpStatus status) {
        assertThat(this.resolver.resolve(new ErrorResponseException(status), WebErrors.INTERNAL_ERROR))
                .isEqualTo(status);
    }

    /** 支持 ResponseStatus 的别名、继承、组合注解与包装原因。 */
    @Test
    void honorsResponseStatusAnnotations() {
        for (final Throwable error : new Throwable[] {
            new MissingResourceException(),
            new InheritedMissingResourceException(),
            new ComposedMissingResourceException(),
            new RuntimeException(new MissingResourceException())
        }) {
            assertThat(this.resolver.resolve(error, WebErrors.INTERNAL_ERROR)).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    /** 外层声明的状态优先于更深的注解，普通内层参数异常不会误降级为四百。 */
    @Test
    void outerStatusWinsAndUnknownWrappersStayServerErrors() {
        assertThat(this.resolver.resolve(
                        new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, new MissingResourceException()),
                        WebErrors.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(this.resolver.resolve(
                        new RuntimeException(new IllegalArgumentException()), WebErrors.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(this.resolver.resolve(new IllegalArgumentException(), WebErrors.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 循环 cause 链不能阻塞异常出口。 */
    @Test
    void terminatesOnCyclicCauseChain() {
        final RuntimeException first = new RuntimeException();
        final RuntimeException second = new RuntimeException(first);
        first.initCause(second);
        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThat(this.resolver.resolve(first, WebErrors.INTERNAL_ERROR))
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** 空校验集合没有调用方错误的依据，应按未知服务端异常处理。 */
    @Test
    void emptyViolationsAreNotCallerErrors() {
        assertThat(this.resolver.resolve(new ConstraintViolationException(Set.of()), WebErrors.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
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
