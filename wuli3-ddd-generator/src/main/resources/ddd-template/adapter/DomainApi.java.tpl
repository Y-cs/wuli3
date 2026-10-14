package {{basePackage}}.adapter.api.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将 {{domainType}} 应用契约适配为 HTTP 接口。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
@RestController
@RequestMapping("/api/{{domain}}")
public final class {{domainType}}Api {
    private final {{domainType}}QueryService service;

    /** 注入共享业务用例。 */
    public {{domainType}}Api(final {{domainType}}QueryService service) {
        this.service = service;
    }

    /** 查询示例聚合状态。 */
    @GetMapping("/status")
    public {{domainType}}StatusDto status() {
        return this.service.status();
    }
}
