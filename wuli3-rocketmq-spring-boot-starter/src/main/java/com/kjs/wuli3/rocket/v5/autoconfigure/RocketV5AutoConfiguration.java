package com.kjs.wuli3.rocket.v5.autoconfigure;

import com.kjs.wuli3.event.autoconfigure.ConditionalOnMissingRoutingEventTransport;
import com.kjs.wuli3.event.autoconfigure.EventAutoConfiguration;
import com.kjs.wuli3.rocket.autoconfigure.RocketCommonAutoConfiguration;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.v5.RocketV5PublishOptions;
import com.kjs.wuli3.rocket.v5.RocketV5RemoteEventTransport;
import java.time.Clock;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 按应用提供的 Producer 注册 v5 传输，客户端生命周期由业务管理。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
@AutoConfiguration(after = RocketCommonAutoConfiguration.class, before = EventAutoConfiguration.class)
@ConditionalOnClass(
        name = {
            "org.apache.rocketmq.client.apis.ClientServiceProvider",
            "org.apache.rocketmq.client.apis.producer.Producer"
        })
public class RocketV5AutoConfiguration {
    /** 提供可覆盖的 SDK 工厂，不创建网络客户端。 */
    @Bean
    @ConditionalOnMissingBean(ClientServiceProvider.class)
    ClientServiceProvider rocketV5ClientServiceProvider() {
        return ClientServiceProvider.loadService();
    }

    /** 创建 v5 传输，业务可按 v5 选项类型覆盖。 */
    @Bean
    @ConditionalOnBean(Producer.class)
    @ConditionalOnMissingRoutingEventTransport(optionsType = RocketV5PublishOptions.class)
    RocketV5RemoteEventTransport rocketV5RemoteEventTransport(
            final Producer producer, final ClientServiceProvider provider, final RocketMessageWrapperEncoder encoder) {
        return new RocketV5RemoteEventTransport(producer, provider, encoder, Clock.systemUTC());
    }
}
