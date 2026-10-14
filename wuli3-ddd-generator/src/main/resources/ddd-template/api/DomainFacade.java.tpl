package {{basePackage}}.api.facade;

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;

/**
 * {{domainType}} 业务能力的内部 RPC 契约。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public interface {{domainType}}Facade {
    /** 查询示例聚合的状态。 */
    {{domainType}}StatusDto status();
}
