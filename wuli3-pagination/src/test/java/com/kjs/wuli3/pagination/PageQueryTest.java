package com.kjs.wuli3.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 分页查询契约的边界测试。 */
class PageQueryTest {

    @Test
    void shouldFallBackToDefaultsWhenPageAndSizeNotProvided() {
        assertThat(new PageQuery(0, 0).page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(new PageQuery(0, 0).pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
        assertThat(new PageQuery().page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(new PageQuery().pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
        assertThat(new PageQuery(-1, -1).page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(new PageQuery(-1, -1).pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }

    @Test
    void shouldKeepSortPriorityAndMakeListImmutable() {
        final PageQuery query = new PageQuery(1, 20, List.of(new Sort("createdAt", SortDirection.DESC)));

        assertThat(query.sorts()).containsExactly(new Sort("createdAt", SortDirection.DESC));
        assertThatThrownBy(() -> query.sorts().add(new Sort("id", SortDirection.ASC)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidPageSizeWithErrorCode() {
        assertThatThrownBy(() -> new PageQuery(1, PageQuery.MAX_PAGE_SIZE + 1))
                .isInstanceOf(ErrorCodeException.class)
                .extracting(exception -> ((ErrorCodeException) exception).getErrorCode())
                .isEqualTo(CommonErrors.ILLEGAL_ARGUMENT);
    }
}
