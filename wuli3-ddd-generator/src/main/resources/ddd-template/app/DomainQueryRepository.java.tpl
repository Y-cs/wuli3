package {{basePackage}}.app.repository.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.sharedkernel.EntityId;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;

/**
 * 只读查询投影仓储，不承担聚合写入。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
public interface {{domainType}}QueryRepository {
    /** 按标识读取，不存在时抛出统一参数错误。 */
    {{domainType}}StatusDto findById(final EntityId id);

    /** 按白名单排序字段分页。 */
    PageResult<{{domainType}}StatusDto> page(final PageQuery query);
}
