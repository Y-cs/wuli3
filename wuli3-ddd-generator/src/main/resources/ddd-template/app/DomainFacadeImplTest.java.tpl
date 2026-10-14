package {{basePackage}}.app.facade.{{domainPackage}};

import static org.assertj.core.api.Assertions.assertThat;

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}QueryService;
import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;
import org.junit.jupiter.api.Test;

/**
 * 验证入口复用应用查询服务并保持结果契约。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
final class {{domainType}}FacadeImplTest {
    /** 入口直接返回共享用例结果，不重复转换或业务编排。 */
    @Test
    void delegatesToQueryService() {
        final var expected = new {{domainType}}StatusDto(new EntityId("sample-1"), Status.ACTIVE);
        final var service = new {{domainType}}QueryService(() -> expected);
        final var entry = new {{domainType}}FacadeImpl(service);

        assertThat(entry.status()).isSameAs(expected);
    }
}
