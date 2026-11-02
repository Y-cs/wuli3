package com.kjs.wuli3.web.autoconfigure;

import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import com.kjs.wuli3.web.error.WebErrorStatusResolver;
import com.kjs.wuli3.web.internal.handler.DefaultWebErrorStatusResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Configures the web-facing error model.
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration(after = CoreSpringBootAutoConfiguration.class)
public class WebErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ErrorResolver.class)
    ErrorResolver errorResolver(final ApplicationServiceProperties properties) {
        return new ErrorResolver(properties.getServiceCode());
    }

    @Bean
    @ConditionalOnMissingBean
    WebErrorStatusResolver webErrorStatusResolver() {
        return new DefaultWebErrorStatusResolver();
    }
}
