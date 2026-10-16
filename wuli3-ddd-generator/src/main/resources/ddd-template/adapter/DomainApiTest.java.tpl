package {{basePackage}}.adapter.api.{{domainPackage}};

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}Service;
import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证入口将增改、查询、删除和分页交给同一应用用例。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
final class {{domainType}}ApiTest {
    /** 保持输入标识、分页参数和结果对象，不重复编排。 */
    @Test
    void delegatesCrudAndPage() {
        final var service = mock({{domainType}}Service.class);
        final var entry = new {{domainType}}Api(service);
        final var id = new EntityId("sample-1");
        final var value = new {{domainType}}StatusDto(id, Status.INACTIVE);
        final var query = new PageQuery(1, 20);
        final var result = new PageResult<>(List.of(value), 1, 1, 20);
        when(service.save(value)).thenReturn(value);
        when(service.findById(id)).thenReturn(value);
        when(service.page(query)).thenReturn(result);
        assertThat(entry.save(value)).isSameAs(value);
        assertThat(entry.findById(id.value())).isSameAs(value);
        assertThat(entry.page(query)).isSameAs(result);
        entry.deleteById(id.value());
        verify(service).deleteById(id);
    }
}
