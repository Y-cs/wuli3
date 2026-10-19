package com.kjs.wuli3.pagination;

import com.kjs.wuli3.core.assertion.Asserts;
import java.io.Serializable;
import java.util.List;

/**
 * 应用层分页查询结果。
 *
 * 注意：分页容器支持序列化；用于 RPC 时，调用方须保证结果元素及其嵌套对象也可序列化。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
public record PageResult<T>(List<T> items, long total, long pageNumber, long pageSize) implements Serializable {

    public PageResult {
        Asserts.whenNull(items).throwIllegalArgumentException("分页结果内容不能为空");
        Asserts.whenFalse(total >= 0).throwIllegalArgumentException("总记录数不能小于 0");
        Asserts.whenFalse(pageNumber > 0).throwIllegalArgumentException("页码必须大于 0");
        Asserts.whenFalse(pageSize > 0).throwIllegalArgumentException("分页大小必须大于 0");
        items = List.copyOf(items);
    }
}
