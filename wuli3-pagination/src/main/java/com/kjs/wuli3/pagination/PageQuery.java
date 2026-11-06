package com.kjs.wuli3.pagination;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import java.io.Serializable;
import java.util.List;

/**
 * 应用层分页查询条件；页码从 1 开始，排序条件按列表顺序表示优先级。
 *
 * 注意：page 和 pageSize 缺省（0 或负数）时回落到默认值 1 和 10，超过上限时抛出错误码异常；
 * 查询条件及排序对象支持序列化，供内部 RPC 契约传播；调用双方仍需保持契约版本兼容。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
public record PageQuery(int page, int pageSize, List<Sort> sorts) implements Serializable {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 200;

    public PageQuery {
        page = page < 1 ? DEFAULT_PAGE : page;
        pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        Asserts.whenFalse(pageSize <= MAX_PAGE_SIZE)
                .throwException(CommonErrors.ILLEGAL_ARGUMENT, "分页大小不能超过 " + MAX_PAGE_SIZE);
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
    }

    /** 使用默认页码和分页大小创建查询条件。 */
    public PageQuery() {
        this(DEFAULT_PAGE, DEFAULT_PAGE_SIZE, List.of());
    }

    public PageQuery(final int page, final int pageSize) {
        this(page, pageSize, List.of());
    }
}
