package {{basePackage}}.api.dto.{{domainPackage}};

import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;

/**
 * {{domainType}} 状态查询结果。
 *
 * @param id 聚合标识
 * @param status 生命周期状态
 * @author GuoYang create on 2026/9/7 15:30
 */
public record {{domainType}}StatusDto(EntityId id, Status status) {}
