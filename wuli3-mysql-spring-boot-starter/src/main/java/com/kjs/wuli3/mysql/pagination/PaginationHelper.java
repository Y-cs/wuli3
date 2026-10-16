package com.kjs.wuli3.mysql.pagination;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import com.kjs.wuli3.pagination.Sort;
import com.kjs.wuli3.pagination.SortDirection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 在应用分页契约与 MyBatis-Plus 分页模型之间进行边界转换。 */
public final class PaginationHelper {

    private PaginationHelper() {}

    public static <T> Page<T> toPage(final PageQuery query) {
        return toPage(query, Function.identity());
    }

    /** 将应用查询条件转换为 MyBatis-Plus 分页参数，并通过字段映射拒绝原始 SQL 排序片段。 */
    public static <T> Page<T> toPage(final PageQuery query, final Function<String, String> fieldMapper) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(fieldMapper, "fieldMapper");
        final Page<T> page = new Page<>(query.pageNumber(), query.pageSize());
        final List<Sort> sorts = query.sorts();
        if (!sorts.isEmpty()) {
            page.addOrder(
                    sorts.stream().map(sort -> toOrderItem(sort, fieldMapper)).collect(Collectors.toList()));
        }
        return page;
    }

    /** 将 MyBatis-Plus 查询结果转换为应用层分页结果。 */
    public static <T> PageResult<T> toPageResult(final IPage<T> page) {
        Objects.requireNonNull(page, "page");
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T, R> PageResult<R> toPageResult(final IPage<T> page, final Function<T, R> mapper) {
        Objects.requireNonNull(page, "page");
        return new PageResult<>(
                Optional.ofNullable(page.getRecords()).orElse(List.of()).stream()
                        .map(mapper)
                        .toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    private static com.baomidou.mybatisplus.core.metadata.OrderItem toOrderItem(
            final Sort sort, final Function<String, String> fieldMapper) {
        final String mappedField = Objects.requireNonNull(fieldMapper.apply(sort.field()), "mapped sort field");
        return sort.direction() == SortDirection.ASC
                ? com.baomidou.mybatisplus.core.metadata.OrderItem.asc(mappedField)
                : com.baomidou.mybatisplus.core.metadata.OrderItem.desc(mappedField);
    }
}
