package {{basePackage}}.app.service.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.repository.{{domainPackage}}.{{domainType}}QueryRepository;
import {{basePackage}}.domain.{{domainPackage}}.{{domainType}};
import {{basePackage}}.domain.{{domainPackage}}.{{domainType}}Repository;
import {{basePackage}}.sharedkernel.EntityId;
import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编排 {{domainType}} 增删改查用例，供 HTTP 与内部 Facade 复用。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Service
@RequiredArgsConstructor
public class {{domainType}}Service {
    private final {{domainType}}QueryRepository repository;
    private final {{domainType}}Repository aggregates;

    /** 新增或修改聚合状态。 */
    @Transactional
    public {{domainType}}StatusDto save(final {{domainType}}StatusDto entry) {
        Asserts.whenNull(entry).throwIllegalArgumentException("保存对象不能为空");
        this.aggregates.save(new {{domainType}}(entry.id(), entry.status()));
        return entry;
    }

    /** 按标识查询投影。 */
    public {{domainType}}StatusDto findById(final EntityId id) {
        Asserts.whenNull(id).throwIllegalArgumentException("标识不能为空");
        return this.repository.findById(id);
    }

    /** 按标识删除投影。 */
    @Transactional
    public void deleteById(final EntityId id) {
        Asserts.whenNull(id).throwIllegalArgumentException("标识不能为空");
        this.aggregates.deleteById(id);
    }
    /** 分页查询投影。 */
    public PageResult<{{domainType}}StatusDto> page(final PageQuery query) {
        Asserts.whenNull(query).throwIllegalArgumentException("分页条件不能为空");
        return this.repository.page(query);
    }
}
