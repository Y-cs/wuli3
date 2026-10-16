package {{basePackage}}.domain.{{domainPackage}};

import {{basePackage}}.sharedkernel.EntityId;

/**
 * {{domainType}} 聚合仓储契约；save 同时覆盖新增和修改。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public interface {{domainType}}Repository {
    /** 新增或修改聚合。 */
    {{domainType}} save(final {{domainType}} aggregate);

    /** 按标识加载聚合。 */
    {{domainType}} findById(final EntityId id);

    /** 按标识删除聚合。 */
    void deleteById(final EntityId id);
}
