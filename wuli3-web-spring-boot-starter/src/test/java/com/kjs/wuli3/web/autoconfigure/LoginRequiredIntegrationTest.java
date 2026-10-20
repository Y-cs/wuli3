package com.kjs.wuli3.web.autoconfigure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.web.auth.LoginRequired;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证 {@link LoginRequired} 的 MVC 拦截和 401 输出。
 *
 * @author lipt create on 2026/10/20 10:00
 */
@SpringBootTest(
        classes = {
            LoginRequiredIntegrationTest.TestApplication.class,
            LoginRequiredIntegrationTest.ControllerConfiguration.class,
        })
@AutoConfigureMockMvc
class LoginRequiredIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousRequestIsRejectedWithUnauthorized() throws Exception {
        this.mockMvc
                .perform(get("/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEB.UNAUTHORIZED"));
    }

    @Test
    void authenticatedRequestPasses() throws Exception {
        this.mockMvc
                .perform(get("/protected")
                        .header(AuthContext.PRINCIPAL_TYPE, "CUSTOMER")
                        .header(AuthContext.PRINCIPAL_ID, "42")
                        .header(AuthContext.PRINCIPAL_NAME, "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void unannotatedEndpointAllowsAnonymous() throws Exception {
        this.mockMvc.perform(get("/public")).andExpect(status().isOk());
    }

    @Test
    void typeLevelDeclarationAppliesToAllEndpoints() throws Exception {
        this.mockMvc.perform(get("/protected-type")).andExpect(status().isUnauthorized());
    }

    /** 启动包含自动配置的最小应用。 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}

    /** 注册测试控制器。 */
    @TestConfiguration
    static class ControllerConfiguration {
        /** 注册方法级标注控制器。 */
        @Bean
        MethodController methodController() {
            return new MethodController();
        }

        /** 注册类型级标注控制器。 */
        @Bean
        TypeController typeController() {
            return new TypeController();
        }
    }

    /** 提供方法级登录声明。 */
    @RestController
    static class MethodController {
        /** 匿名请求应被拒绝。 */
        @LoginRequired
        @GetMapping("/protected")
        String protectedEndpoint() {
            return "protected";
        }

        /** 匿名请求可以访问。 */
        @GetMapping("/public")
        String publicEndpoint() {
            return "public";
        }
    }

    /** 提供类型级登录声明。 */
    @LoginRequired
    @RestController
    static class TypeController {
        /** 继承类型级登录声明。 */
        @GetMapping("/protected-type")
        String protectedEndpoint() {
            return "protected";
        }
    }
}
