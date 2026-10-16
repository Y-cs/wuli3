package {{basePackage}}.adapter.api.{{domainPackage}};

import {{basePackage}}.api.dto.{{domainPackage}}.{{domainType}}StatusDto;
import {{basePackage}}.app.service.{{domainPackage}}.{{domainType}}Service;
import {{basePackage}}.sharedkernel.EntityId;
import com.kjs.wuli3.pagination.PageQuery;
import com.kjs.wuli3.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将 {{domainType}} 应用契约适配为 HTTP 接口。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
@RestController
@RequestMapping("/api/{{domain}}")
@RequiredArgsConstructor
public final class {{domainType}}Api {
    private final {{domainType}}Service service;

    /** 新增或修改聚合状态。 */
    @PostMapping
    public {{domainType}}StatusDto save(@RequestBody final {{domainType}}StatusDto entry) {
        return this.service.save(entry);
    }

    /** 按标识查询聚合。 */
    @GetMapping("/{id}")
    public {{domainType}}StatusDto findById(@PathVariable final String id) {
        return this.service.findById(new EntityId(id));
    }

    /** 按标识删除聚合。 */
    @DeleteMapping("/{id}")
    public void deleteById(@PathVariable final String id) {
        this.service.deleteById(new EntityId(id));
    }
    /** 分页查询投影。 */
    @org.springframework.web.bind.annotation.PostMapping("/page")
    public PageResult<{{domainType}}StatusDto> page(@RequestBody final PageQuery query) {
        return this.service.page(query);
    }
}
