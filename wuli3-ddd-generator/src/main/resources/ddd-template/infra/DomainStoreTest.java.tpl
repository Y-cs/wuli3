package {{basePackage}}.infra.persistence.{{domainPackage}};

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}Service;
import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.Sort;
import com.kjs.wuli3.pagination.SortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证无数据库模式中两个仓储共享持久状态与分页语义。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
final class {{domainType}}StoreTest {
    /** 保存更新可被查询读取，分页元数据与删除结果保持一致。 */
    @Test
    void savesUpdatesPagesAndDeletes() {
        final var store = new {{domainType}}Store();
        final var service = new {{domainType}}Service(new {{domainType}}QueryRepositoryImpl(store), new {{domainType}}RepositoryImpl(store));
        final var first = new EntityId("1");
        final var second = new EntityId("2");
        service.save(new {{domainType}}StatusDto(first, Status.ACTIVE));
        service.save(new {{domainType}}StatusDto(second, Status.ACTIVE));
        service.save(new {{domainType}}StatusDto(first, Status.INACTIVE));
        assertThat(service.findById(first).status()).isEqualTo(Status.INACTIVE);
        final var page = service.page(new PageQuery(2, 1, List.of(new Sort("id", SortDirection.DESC))));
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).containsExactly(new {{domainType}}StatusDto(first, Status.INACTIVE));
        assertThat(service.page(new PageQuery(3, 1)).items()).isEmpty();
        assertThatThrownBy(() -> service.page(new PageQuery(1, 10, List.of(new Sort("id desc;", SortDirection.ASC)))))
                .isInstanceOf(ErrorCodeException.class);
        service.deleteById(first);
        service.deleteById(first);
        assertThatThrownBy(() -> service.findById(first)).isInstanceOf(ErrorCodeException.class);
        assertThat(service.page(new PageQuery(1, 20)).total()).isEqualTo(1);
    }
}
