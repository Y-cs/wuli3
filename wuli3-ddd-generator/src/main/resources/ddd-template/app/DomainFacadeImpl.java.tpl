package {{basePackage}}.app.facade.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.api.facade.{{domainType}}Facade;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}Service;
import {{basePackage}}.sharedkernel.EntityId;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import lombok.RequiredArgsConstructor;
{{facadeRpcImport}}import org.springframework.stereotype.Service;

/**
 * 实现 {{domainType}} 内部调用契约；委托核心服务。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
{{facadeRpcAnnotation}}@Service
@RequiredArgsConstructor
public class {{domainType}}FacadeImpl implements {{domainType}}Facade {
    private final {{domainType}}Service service;

    /** 委托核心服务新增或修改。 */
    @Override
    public {{domainType}}StatusDto save(final {{domainType}}StatusDto entry) {
        return this.service.save(entry);
    }

    /** 委托核心服务按标识查询。 */
    @Override
    public {{domainType}}StatusDto findById(final EntityId id) {
        return this.service.findById(id);
    }

    /** 委托核心服务按标识删除。 */
    @Override
    public void deleteById(final EntityId id) {
        this.service.deleteById(id);
    }
    /** 分页查询投影。 */
    @Override
    public PageResult<{{domainType}}StatusDto> page(final PageQuery query) {
        return this.service.page(query);
    }
}
