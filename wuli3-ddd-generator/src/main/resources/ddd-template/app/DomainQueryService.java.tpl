package {{basePackage}}.app.service.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.repository.{{domainPackage}}.{{domainType}}QueryRepository;
import org.springframework.stereotype.Service;

/**
 * 编排 {{domainType}} 查询用例，供 HTTP 与内部 Facade 复用。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Service
public class {{domainType}}QueryService {
    private final {{domainType}}QueryRepository repository;

    /** 注入查询仓储。 */
    public {{domainType}}QueryService(final {{domainType}}QueryRepository repository) {
        this.repository = repository;
    }

    /** 查询示例状态，直接返回可复用的传播对象。 */
    public {{domainType}}StatusDto status() {
        return this.repository.status();
    }
}
