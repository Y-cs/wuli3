package com.wulicode.wuli3.audit.autoconfigure;

import com.wulicode.wuli3.audit.protocol.AuditLogPublishOptions;
import com.wulicode.wuli3.audit.protocol.AuditLogStore;
import com.wulicode.wuli3.audit.protocol.StoreBackedAuditLogEventTransport;
import com.wulicode.wuli3.event.autoconfigure.ConditionalOnMissingRoutingEventTransport;
import com.wulicode.wuli3.event.autoconfigure.EventAutoConfiguration;
import com.wulicode.wuli3.event.remote.RoutingEventTransport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 自动配置独立审计服务侧基于 {@link AuditLogStore} 的写入传输实现。
 *
 * <p>在 {@link AuditLogAutoConfiguration} 之前执行，确保 store-backed 传输优先于客户端侧配置生效。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration(before = {EventAutoConfiguration.class, AuditLogAutoConfiguration.class})
@ConditionalOnProperty(prefix = "wuli3.audit-log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(AuditLogStore.class)
    @ConditionalOnMissingRoutingEventTransport(optionsType = AuditLogPublishOptions.class)
    RoutingEventTransport<?> storeBackedAuditLogEventTransport(final AuditLogStore auditLogStore) {
        return new StoreBackedAuditLogEventTransport(auditLogStore);
    }
}
