package com.kjs.wuli3.pagination;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 应用层分页查询条件；页码从 1 开始，排序条件按列表顺序表示优先级。
 *
 * 注意：构造时 page 和 pageSize 缺省（0 或负数）时回落到默认值 1 和 10，超过上限时抛出错误码异常；
 * 查询条件及排序对象支持序列化，供内部 RPC 契约传播；调用双方仍需保持契约版本兼容。
 *
 * 注意：setter 直接更新属性，不执行构造器中的校验或集合复制；调用方须保证更新后的值有效。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public final class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 200;

    private int page;
    private int pageSize;
    private List<Sort> sorts;

    /** 创建查询条件，归一化缺省页码和大小，并复制排序列表。 */
    public PageQuery(final int page, final int pageSize, final List<Sort> sorts) {
        this.page = page < 1 ? PageQuery.DEFAULT_PAGE : page;
        this.pageSize = pageSize < 1 ? PageQuery.DEFAULT_PAGE_SIZE : pageSize;
        if (this.pageSize > PageQuery.MAX_PAGE_SIZE) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "分页大小不能超过 " + PageQuery.MAX_PAGE_SIZE)
                    .withOrigin(ErrorOrigin.CALLER);
        }
        this.sorts = sorts == null ? List.of() : List.copyOf(sorts);
    }

    /** 使用默认页码和分页大小创建查询条件。 */
    public PageQuery() {
        this(PageQuery.DEFAULT_PAGE, PageQuery.DEFAULT_PAGE_SIZE, List.of());
    }

    /** 使用指定页码和大小创建不带排序的查询条件。 */
    public PageQuery(final int page, final int pageSize) {
        this(page, pageSize, List.of());
    }
}
