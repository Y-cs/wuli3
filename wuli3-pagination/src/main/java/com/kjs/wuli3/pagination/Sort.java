package com.kjs.wuli3.pagination;

import com.kjs.wuli3.core.assertion.Asserts;
import java.io.Serializable;

/**
 * 应用层排序条件；field 是应用查询字段，不是数据库列名或 SQL 片段。
 *
 * 注意：支持作为分页查询对象的一部分序列化；持久化适配器仍须对白名单字段进行映射。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
public record Sort(String field, SortDirection direction) implements Serializable {

    public Sort {
        Asserts.whenBlank(field).throwIllegalArgumentException("排序字段不能为空");
        Asserts.whenNull(direction).throwIllegalArgumentException("排序方向不能为空");
    }
}
