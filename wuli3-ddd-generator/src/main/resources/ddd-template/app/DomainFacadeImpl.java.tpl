package {{basePackage}}.app.facade.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.api.facade.{{domainType}}Facade;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}QueryService;
import org.springframework.stereotype.Service;

/**
 * 实现 {{domainType}} 内部调用契约；RPC 发布由接入或启动配置负责。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Service
public class {{domainType}}FacadeImpl implements {{domainType}}Facade {
    private final {{domainType}}QueryService service;

    /** 注入共享业务用例。 */
    public {{domainType}}FacadeImpl(final {{domainType}}QueryService service) {
        this.service = service;
    }

    /** 委托核心服务查询状态，不重复业务编排。 */
    @Override
    public {{domainType}}StatusDto status() {
        return this.service.status();
    }
}
