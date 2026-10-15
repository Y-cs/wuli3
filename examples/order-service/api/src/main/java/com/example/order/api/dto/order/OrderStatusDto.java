package com.example.order.api.dto.order;

import com.example.order.sharedkernel.EntityId;
import com.example.order.sharedkernel.Status;

/**
 * Order 状态查询结果。
 *
 * @param id 聚合标识
 * @param status 生命周期状态
 * @author GuoYang create on 2026/9/7 15:30
 */
public record OrderStatusDto(EntityId id, Status status) {}
