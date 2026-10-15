package {{basePackage}}.infra.persistence.{{domainPackage}};

import {{basePackage}}.domain.{{domainPackage}}.{{domainType}};
import {{basePackage}}.domain.{{domainPackage}}.{{domainType}}Repository;
{{repositoryImports}}import {{basePackage}}.sharedkernel.EntityId;
import org.springframework.stereotype.Repository;

/**
 * {{domainType}} 聚合仓储的基础设施实现。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
@Repository
public class {{domainType}}RepositoryImpl implements {{domainType}}Repository {
{{repositoryFields}}{{repositoryConstructor}}
    /** 加载示例聚合。 */
    @Override
    public {{domainType}} load() {
{{repositoryLoad}}
    }
}
