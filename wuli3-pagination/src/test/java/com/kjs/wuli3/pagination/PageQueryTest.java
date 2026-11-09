package com.kjs.wuli3.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 分页查询契约的边界测试。 */
class PageQueryTest {

    @Test
    void shouldFallBackToDefaultsWhenPageAndSizeNotProvided() {
        assertThat(new PageQuery(0, 0).getPage()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(new PageQuery(0, 0).getPageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
        assertThat(new PageQuery().getPage()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(new PageQuery().getPageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
        assertThat(new PageQuery(-1, -1).getPage()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(new PageQuery(-1, -1).getPageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }

    @Test
    void shouldKeepSortPriorityAndMakeListImmutable() {
        final PageQuery query = new PageQuery(1, 20, List.of(new Sort("createdAt", SortDirection.DESC)));

        assertThat(query.getSorts()).containsExactly(new Sort("createdAt", SortDirection.DESC));
        assertThatThrownBy(() -> query.getSorts().add(new Sort("id", SortDirection.ASC)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidPageSizeWithErrorCode() {
        assertThatThrownBy(() -> new PageQuery(1, PageQuery.MAX_PAGE_SIZE + 1))
                .isInstanceOfSatisfying(ErrorCodeException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrors.ILLEGAL_ARGUMENT);
                    assertThat(exception.getOrigin()).isEqualTo(ErrorOrigin.CALLER);
                    assertThat(exception).hasMessage("分页大小不能超过 " + PageQuery.MAX_PAGE_SIZE);
                });
    }
}
