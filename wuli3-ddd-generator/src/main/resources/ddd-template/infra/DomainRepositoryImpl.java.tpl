package {{basePackage}}.infra.persistence.{{domainPackage}};

import {{basePackage}}.domain.{{domainPackage}}.{{domainType}};
import {{basePackage}}.domain.{{domainPackage}}.{{domainType}}Repository;
{{repositoryImports}}import {{basePackage}}.sharedkernel.EntityId;
import org.springframework.stereotype.Repository;
import com.kjs.wuli3.core.assertion.Asserts;
import lombok.RequiredArgsConstructor;

/**
 * {{domainType}} 聚合仓储的基础设施实现。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
@Repository
@RequiredArgsConstructor
public class {{domainType}}RepositoryImpl implements {{domainType}}Repository {
{{repositoryFields}}
    /** 新增或修改聚合。 */
    @Override
    public {{domainType}} save(final {{domainType}} aggregate) {
{{repositorySave}}
    }

    /** 按标识加载聚合。 */
    @Override
    public {{domainType}} findById(final EntityId id) {
{{repositoryLoad}}
    }

    /** 按标识删除聚合。 */
    @Override
    public void deleteById(final EntityId id) {
{{repositoryDelete}}
    }
}
