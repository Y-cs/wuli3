package com.kjs.wuli3.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证分页契约的完整对象图能够序列化往返并保留不可变语义。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
final class PaginationSerializationTest {
    /** 查询往返时保留页码、大小和排序优先级，包括嵌套排序方向。 */
    @Test
    void roundTripsQueryWithSorts() throws Exception {
        final PageQuery original = new PageQuery(
                3, 25, List.of(new Sort("createdAt", SortDirection.DESC), new Sort("id", SortDirection.ASC)));

        final PageQuery restored = (PageQuery) PaginationSerializationTest.roundTrip(original);

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.sorts())
                .containsExactly(new Sort("createdAt", SortDirection.DESC), new Sort("id", SortDirection.ASC));
        assertThatThrownBy(() -> restored.sorts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    /** 结果往返时保留实际记录与分页元数据，而不退化为空容器。 */
    @Test
    void roundTripsResultWithItems() throws Exception {
        final PageResult<String> original = new PageResult<>(List.of("order-21", "order-22"), 37, 2, 20);

        final PageResult<?> restored = (PageResult<?>) PaginationSerializationTest.roundTrip(original);

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.items()).isEqualTo(List.of("order-21", "order-22"));
        assertThatThrownBy(() -> restored.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    /** 仅反序列化测试即时生成的可信字节，验证整个对象图而非仅检查标记接口。 */
    private static Object roundTrip(final Serializable value) throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (final ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
        }
        try (final ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            return input.readObject();
        }
    }
}
