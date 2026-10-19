package {{basePackage}}.api.facade;

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.sharedkernel.EntityId;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;

/**
 * {{domainType}} 业务能力的内部 RPC 契约。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public interface {{domainType}}Facade {
    /** 新增或修改聚合状态。 */
    {{domainType}}StatusDto save(final {{domainType}}StatusDto entry);

    /** 按标识查询聚合投影。 */
    {{domainType}}StatusDto findById(final EntityId id);

    /** 按标识删除聚合。 */
    void deleteById(final EntityId id);
    /** 分页查询投影。 */
    PageResult<{{domainType}}StatusDto> page(final PageQuery query);
}
