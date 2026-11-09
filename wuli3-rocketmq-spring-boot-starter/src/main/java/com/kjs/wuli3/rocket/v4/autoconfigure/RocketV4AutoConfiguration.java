package com.kjs.wuli3.rocket.v4.autoconfigure;

import com.kjs.wuli3.event.autoconfigure.ConditionalOnMissingRoutingEventTransport;
import com.kjs.wuli3.event.autoconfigure.EventAutoConfiguration;
import com.kjs.wuli3.rocket.autoconfigure.RocketCommonAutoConfiguration;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.v4.RocketRemoteEventTransport;
import com.kjs.wuli3.rocket.v4.RocketV4PublishOptions;
import com.kjs.wuli3.rocket.v4.RocketV4TransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
            final RocketMQTemplate template,
            final RocketMessageWrapperEncoder encoder,
            final RocketLocalTransactionRegistry transactionRegistry) {
        return new RocketRemoteEventTransport(template, encoder, transactionRegistry);
    }

    /** 注册事务消息回调分发器；业务自定义事务监听器时自动让位。 */
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean(RocketMQLocalTransactionListener.class)
    RocketV4TransactionListener rocketV4TransactionListener(final RocketLocalTransactionRegistry transactionRegistry) {
        return new RocketV4TransactionListener(transactionRegistry);
    }
}
