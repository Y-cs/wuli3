package {{basePackage}}.infra.persistence.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.domain.{{domainPackage}}.{{domainType}};
import {{basePackage}}.sharedkernel.EntityId;
import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import com.kjs.wuli3.pagination.SortDirection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 无数据库模式的进程内存储，读写仓储共享不可变状态快照；重启后数据清空。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
@Component
public final class {{domainType}}Store {
    private final ConcurrentMap<EntityId, {{domainType}}StatusDto> entries = new ConcurrentHashMap<>();

    /** 保存快照，避免外部修改聚合影响已保存状态。 */
    public void save(final {{domainType}} aggregate) {
        this.entries.put(aggregate.id(), new {{domainType}}StatusDto(aggregate.id(), aggregate.status()));
    }

    /** 按标识读取，不存在时抛出统一参数错误。 */
    public {{domainType}}StatusDto findById(final EntityId id) {
        final var entry = this.entries.get(id);
        Asserts.whenNull(entry).throwException({{basePackage}}.sharedkernel.RecordErrors.NOT_FOUND);
        // 前述断言已处理业务缺失；此处仅向空安全检查器证明不变量。
        return java.util.Objects.requireNonNull(entry);
    }

    /** 幂等删除。 */
    public void deleteById(final EntityId id) {
        this.entries.remove(id);
    }

    /** 排序后分页，默认以主键稳定排序。 */
    public PageResult<{{domainType}}StatusDto> page(final PageQuery query) {
        Comparator<{{domainType}}StatusDto> comparator = (left, right) -> 0;
        for (final var sort : query.sorts()) {
            Asserts.whenFalse(sort.field().equals("id") || sort.field().equals("status"))
                    .throwIllegalArgumentException("不支持的排序字段");
            final Comparator<{{domainType}}StatusDto> fieldComparator = sort.field().equals("id")
                    ? Comparator.comparing(entry -> entry.id().value())
                    : Comparator.comparing(entry -> entry.status().name());
            comparator = comparator.thenComparing(
                    sort.direction() == SortDirection.ASC ? fieldComparator : fieldComparator.reversed());
        }
        final var snapshot = this.entries.values().stream()
                .sorted(comparator.thenComparing(entry -> entry.id().value()))
                .toList();
        final var items = snapshot.stream()
                .skip((long) (query.pageNumber() - 1) * query.pageSize())
                .limit(query.pageSize())
                .toList();
        return new PageResult<>(items, snapshot.size(), query.pageNumber(), query.pageSize());
    }
}
