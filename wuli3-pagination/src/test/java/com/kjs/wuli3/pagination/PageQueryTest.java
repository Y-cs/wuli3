package com.kjs.wuli3.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 分页查询契约的边界测试。 */
class PageQueryTest {

    @Test
    void shouldKeepSortPriorityAndMakeListImmutable() {
        final PageQuery query = new PageQuery(1, 20, List.of(new Sort("createdAt", SortDirection.DESC)));

        assertThat(query.sorts()).containsExactly(new Sort("createdAt", SortDirection.DESC));
        assertThatThrownBy(() -> query.sorts().add(new Sort("id", SortDirection.ASC)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThatThrownBy(() -> new PageQuery(1, PageQuery.MAX_PAGE_SIZE + 1)).isInstanceOf(RuntimeException.class);
    }
}
