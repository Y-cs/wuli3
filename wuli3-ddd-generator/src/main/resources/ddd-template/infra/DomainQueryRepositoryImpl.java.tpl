package {{basePackage}}.infra.persistence.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.repository.{{domainPackage}}.{{domainType}}QueryRepository;
{{repositoryImports}}import {{basePackage}}.sharedkernel.EntityId;
{{assertImport}}import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 {{domainType}} 增删改查投影仓储。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
@Repository
@RequiredArgsConstructor
public class {{domainType}}QueryRepositoryImpl implements {{domainType}}QueryRepository {
{{repositoryFields}}
    /** 按标识读取查询投影。 */
    @Override
    public {{domainType}}StatusDto findById(final EntityId id) {
{{queryRepositoryFindById}}
    }

    /** 分页读取查询投影。 */
    @Override
    public PageResult<{{domainType}}StatusDto> page(final PageQuery query) {
{{queryRepositoryPage}}
    }
}
