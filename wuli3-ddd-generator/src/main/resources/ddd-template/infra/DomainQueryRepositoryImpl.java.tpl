package {{basePackage}}.infra.persistence.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.repository.{{domainPackage}}.{{domainType}}QueryRepository;
{{repositoryImports}}import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;
import org.springframework.stereotype.Repository;

/**
 * 直接读取 {{domainType}} 查询投影，不借用聚合仓储还原领域模型。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Repository
public class {{domainType}}QueryRepositoryImpl implements {{domainType}}QueryRepository {
{{repositoryFields}}{{queryRepositoryConstructor}}
    /** 读取示例状态投影。 */
    @Override
    public {{domainType}}StatusDto status() {
{{queryRepositoryStatus}}
    }
}
