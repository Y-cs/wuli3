package com.kjs.wuli3.pagination;

import com.kjs.wuli3.core.assertion.Asserts;
import java.io.Serializable;
import java.util.List;

/**
 * 应用层分页查询条件；页码从 1 开始，排序条件按列表顺序表示优先级。
 *
 * 注意：查询条件及排序对象支持序列化，供内部 RPC 契约传播；调用双方仍需保持契约版本兼容。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
public record PageQuery(int pageNumber, int pageSize, List<Sort> sorts) implements Serializable {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    public PageQuery {
        Asserts.whenFalse(pageNumber > 0).throwIllegalArgumentException("页码必须大于 0");
        Asserts.whenFalse(pageSize > 0 && pageSize <= MAX_PAGE_SIZE)
                .throwIllegalArgumentException("分页大小必须在 1 至 " + MAX_PAGE_SIZE + " 之间");
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
    }

    public PageQuery(final int pageNumber, final int pageSize) {
        this(pageNumber, pageSize, List.of());
    }
}
