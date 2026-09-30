package com.kjs.wuli3.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 使用真实嵌入式容器验证 ERROR dispatch 和应用级错误响应。
 *
 * 注意：容器进入应用前拒绝的非法 HTTP 报文不属于 starter 可接管范围；响应已提交也不重写。
 * 当前 starter 的自动配置在项目支持的嵌入式容器上复用同一 ErrorController 契约。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ContainerErrorIntegrationTest.TestApplication.class,
        properties = {
            "wuli3.web.response.exception-handler-enabled=false",
            "wuli3.web.response.wrapper-enabled=false",
            "server.error.include-message=always"
        })
class ContainerErrorIntegrationTest {

    private final TestRestTemplate restTemplate;

    @Autowired
    ContainerErrorIntegrationTest(final TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** 未匹配路径通过 ERROR dispatch 返回应用默认格式并保留 404。 */
    @Test
    void unmatchedPathUsesApplicationErrorResponse() {
        final ResponseEntity<String> response = this.restTemplate.getForEntity("/missing", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"WEB.NOT_FOUND\"", "\"message\":\"资源不存在\"");
    }

    /** sendError 的容器错误通过同一 ERROR dispatch 输出安全响应。 */
    @Test
    void sendErrorUsesApplicationErrorResponse() {
        final ResponseEntity<String> response = this.restTemplate.getForEntity("/send-error", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"WEB.NOT_FOUND\"").doesNotContain("trace");
    }

    /** 未处理异常在禁用 MVC advice 时由容器 ERROR dispatch 接管并隐藏异常详情。 */
    @Test
    void unhandledExceptionUsesApplicationErrorResponse() {
        final ResponseEntity<String> response = this.restTemplate.getForEntity("/unhandled", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .contains("\"code\":\"WEB.INTERNAL_ERROR\"")
                .doesNotContain("secret stack");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/send-error")
        void sendError(final HttpServletResponse response) throws java.io.IOException {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "secret container message");
        }

        @GetMapping("/unhandled")
        String unhandled() {
            throw new IllegalStateException("secret stack");
        }
    }
}
