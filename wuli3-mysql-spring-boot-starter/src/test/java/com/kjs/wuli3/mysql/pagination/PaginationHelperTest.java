package com.kjs.wuli3.mysql.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import com.kjs.wuli3.pagination.Sort;
import com.kjs.wuli3.pagination.SortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MyBatis-Plus 分页边界转换测试。 */
class PaginationHelperTest {

    @Test
    void shouldMapApplicationSortToMappedColumn() {
        final PageQuery query = new PageQuery(2, 10, List.of(new Sort("createdAt", SortDirection.DESC)));

        final Page<Object> page = PaginationHelper.toPage(query, field -> "order_created_at");

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.orders()).singleElement().satisfies(order -> {
            assertThat(order.getColumn()).isEqualTo("order_created_at");
            assertThat(order.isAsc()).isFalse();
        });
    }

    @Test
    void shouldMapPageResult() {
        final Page<String> page = new Page<>(3, 5, 12);
        page.setRecords(List.of("a", "b"));

        final PageResult<String> result = PaginationHelper.toPageResult(page);

        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.total()).isEqualTo(12);
        assertThat(result.pageNumber()).isEqualTo(3);
        assertThat(result.pageSize()).isEqualTo(5);
    }
}
