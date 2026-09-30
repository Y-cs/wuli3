package com.kjs.wuli3.rocket.v4.autoconfigure;

import com.kjs.wuli3.event.autoconfigure.ConditionalOnMissingRoutingEventTransport;
import com.kjs.wuli3.event.autoconfigure.EventAutoConfiguration;
import com.kjs.wuli3.rocket.autoconfigure.RocketCommonAutoConfiguration;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.v4.RocketRemoteEventTransport;
import com.kjs.wuli3.rocket.v4.RocketV4PublishOptions;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 按应用提供的 Template 注册 v4 传输，不排斥 v5 传输。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
@AutoConfiguration(
        after = RocketCommonAutoConfiguration.class,
        before = EventAutoConfiguration.class,
        afterName = "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
@ConditionalOnClass(RocketMQTemplate.class)
public class RocketV4AutoConfiguration {
    /** 创建 v4 传输，业务可按 v4 选项类型覆盖。 */
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingRoutingEventTransport(optionsType = RocketV4PublishOptions.class)
    RocketRemoteEventTransport rocketMqRemoteEventMessageTransport(
            final RocketMQTemplate template, final RocketMessageWrapperEncoder encoder) {
        return new RocketRemoteEventTransport(template, encoder);
    }
}
