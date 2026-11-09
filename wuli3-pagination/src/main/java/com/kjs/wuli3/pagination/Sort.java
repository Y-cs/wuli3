package com.kjs.wuli3.pagination;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 应用层排序条件；field 是应用查询字段，不是数据库列名或 SQL 片段。
 *
 * 注意：支持作为分页查询对象的一部分序列化；持久化适配器仍须对白名单字段进行映射。
 *
 * 注意：setter 直接更新属性，不执行构造器中的校验；调用方须保证更新后的值有效。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class Sort implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String field;
    private SortDirection direction;

    /** 创建具有明确字段和方向的排序条件。 */
    public Sort(final String field, final SortDirection direction) {
        if (field == null || field.isBlank()) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "排序字段不能为空").withOrigin(ErrorOrigin.CALLER);
        }
        if (direction == null) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "排序方向不能为空").withOrigin(ErrorOrigin.CALLER);
        }
        this.field = field;
        this.direction = direction;
    }
}
