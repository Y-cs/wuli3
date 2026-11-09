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
 * 应用层分页查询结果。
 *
 * 注意：分页容器支持序列化；用于 RPC 时，调用方须保证结果元素及其嵌套对象也可序列化。
 *
 * 注意：setter 直接更新属性，不执行构造器中的校验或集合复制；调用方须保证更新后的值有效。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> items;
    private long total;
    private long page;
    private long pageSize;

    /** 校验分页元数据并复制结果列表。 */
    public PageResult(final List<T> items, final long total, final long page, final long pageSize) {
        if (items == null) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "分页结果内容不能为空").withOrigin(ErrorOrigin.CALLER);
        }
        if (total < 0) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "总记录数不能小于 0").withOrigin(ErrorOrigin.CALLER);
        }
        if (page <= 0) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "页码必须大于 0").withOrigin(ErrorOrigin.CALLER);
        }
        if (pageSize <= 0) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "分页大小必须大于 0").withOrigin(ErrorOrigin.CALLER);
        }
        this.items = List.copyOf(items);
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
