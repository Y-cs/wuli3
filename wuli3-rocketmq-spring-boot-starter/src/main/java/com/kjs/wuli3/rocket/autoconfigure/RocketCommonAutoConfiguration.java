package com.kjs.wuli3.rocket.autoconfigure;

import com.kjs.wuli3.event.autoconfigure.EventAutoConfiguration;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.rocket.internal.RocketContextSupport;
import com.kjs.wuli3.rocket.internal.wrapper.RocketMessageWrapperEncoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动配置两个客户端共享的事件编码与上下文支持。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration
@AutoConfigureBefore(EventAutoConfiguration.class)
public class RocketCommonAutoConfiguration {

    /**
     * 创建默认传播调用标识和可信认证信息的上下文编码器。
     */
    @Bean
    @ConditionalOnMissingBean
    ContextPropagator rocketMqContextEncoder() {
        return new ContextPropagator(ContextPropagator.standardContextEncoder());
    }

    /**
     * 创建消费端上下文解码支持；消费适配器自行决定何时恢复和关闭上下文作用域。
     *
     * @param contextManager  上下文作用域代理器
     * @param contextPropagator 上下文字段编码器
     * @return RocketMQ 上下文支持
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ContextManager.class)
    RocketContextSupport rocketMqContextSupport(
            final ContextManager contextManager, final ContextPropagator contextPropagator) {
        return new RocketContextSupport(contextManager, contextPropagator);
    }

    /**
     * 创建由不同 RocketMQ 客户端实现共享的事件编码器。
     *
     * @param contextReaders 可选的当前上下文读取器
     * @param contextPropagator 上下文字段编码器
     * @return 公共事件编码器
     */
    @Bean
    @ConditionalOnMissingBean
    RocketMessageWrapperEncoder rocketMqEventMessageEncoder(
            final ObjectProvider<ContextReader> contextReaders, final ContextPropagator contextPropagator) {
        return new RocketMessageWrapperEncoder(contextReaders.getIfUnique(), contextPropagator);
    }
}
