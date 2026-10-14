package {{basePackage}}.app.repository.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;

/**
 * {{domainType}} 查询投影仓储；查询不要求还原领域聚合。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
public interface {{domainType}}QueryRepository {
    /** 读取示例状态投影。 */
    {{domainType}}StatusDto status();
}
