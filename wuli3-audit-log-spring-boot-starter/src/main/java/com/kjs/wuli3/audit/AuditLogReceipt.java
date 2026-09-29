package com.kjs.wuli3.audit;

import com.kjs.wuli3.core.assertion.Asserts;
import java.time.Instant;

/**
 * 审计事件发布后返回的生产者侧回执。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record AuditLogReceipt(String eventId, Instant occurredAt) {

    public AuditLogReceipt {
        Asserts.whenBlank(eventId).throwIllegalArgumentException("eventId cannot be blank");
        Asserts.whenNull(occurredAt).throwIllegalArgumentException("occurredAt cannot be null");
    }
}
