package com.kjs.wuli3.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 验证分页类的属性读写及构造器边界。
 *
 * @author GuoYang create on 2026/11/9 14:00
 */
final class PaginationBeanTest {

    /** 查询条件可以通过标准 setter 更新，属性参与值比较。 */
    @Test
    void updatesQueryProperties() {
        final PageQuery query = new PageQuery();
        final List<Sort> sorts = List.of(new Sort("id", SortDirection.ASC));

        query.setPage(2);
        query.setPageSize(25);
        query.setSorts(sorts);

        assertThat(query.getPage()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(25);
        assertThat(query.getSorts()).isEqualTo(sorts);
        assertThat(query).isEqualTo(new PageQuery(2, 25, sorts));
    }

    /** 结果内容和分页元数据均支持标准属性读写。 */
    @Test
    void updatesResultProperties() {
        final PageResult<String> result = new PageResult<>(List.of(), 0, 1, 10);

        result.setItems(List.of("order-21"));
        result.setTotal(21);
        result.setPage(3);
        result.setPageSize(10);

        assertThat(result.getItems()).containsExactly("order-21");
        assertThat(result.getTotal()).isEqualTo(21);
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result).isEqualTo(new PageResult<>(List.of("order-21"), 21, 3, 10));
    }

    /** 排序字段和方向可更新，更新后的值仍可比较。 */
    @Test
    void updatesSortProperties() {
        final Sort sort = new Sort("id", SortDirection.ASC);

        sort.setField("createdAt");
        sort.setDirection(SortDirection.DESC);

        assertThat(sort.getField()).isEqualTo("createdAt");
        assertThat(sort.getDirection()).isEqualTo(SortDirection.DESC);
        assertThat(sort).isEqualTo(new Sort("createdAt", SortDirection.DESC));
    }

    /** 非法构造参数使用统一参数错误码，并明确归属于调用方。 */
    @ParameterizedTest
    @MethodSource("invalidConstructorArguments")
    void rejectsInvalidConstructorArguments(final ThrowingCallable constructor, final String message) {
        assertThatThrownBy(constructor).isInstanceOfSatisfying(ErrorCodeException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrors.ILLEGAL_ARGUMENT);
            assertThat(exception.getOrigin()).isEqualTo(ErrorOrigin.CALLER);
            assertThat(exception).hasMessage(message);
        });
    }

    /** 覆盖结果元数据和排序字段、方向的非法输入边界，故意传入空值验证运行时保护。 */
    @SuppressWarnings("NullAway")
    private static Stream<Arguments> invalidConstructorArguments() {
        return Stream.of(
                Arguments.of((ThrowingCallable) () -> new PageResult<>(null, 0, 1, 10), "分页结果内容不能为空"),
                Arguments.of((ThrowingCallable) () -> new PageResult<>(List.of(), -1, 1, 10), "总记录数不能小于 0"),
                Arguments.of((ThrowingCallable) () -> new PageResult<>(List.of(), 0, 0, 10), "页码必须大于 0"),
                Arguments.of((ThrowingCallable) () -> new PageResult<>(List.of(), 0, 1, 0), "分页大小必须大于 0"),
                Arguments.of((ThrowingCallable) () -> new Sort(null, SortDirection.ASC), "排序字段不能为空"),
                Arguments.of((ThrowingCallable) () -> new Sort("", SortDirection.ASC), "排序字段不能为空"),
                Arguments.of((ThrowingCallable) () -> new Sort(" ", SortDirection.ASC), "排序字段不能为空"),
                Arguments.of((ThrowingCallable) () -> new Sort("id", null), "排序方向不能为空"));
    }
}
