package {{basePackage}}.api.dto.{{domainPackage}};

import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;
import com.kjs.wuli3.core.assertion.Asserts;
import java.io.Serializable;

/**
 * {{domainType}} 状态查询结果。
 *
 * @param id 聚合标识
 * @param status 生命周期状态
 * @author GuoYang create on 2026/9/7 15:30
 */
public record {{domainType}}StatusDto(EntityId id, Status status) implements Serializable {
    /** 校验传播对象的完整性。 */
    public {{domainType}}StatusDto {
        Asserts.whenNull(id).throwIllegalArgumentException("标识不能为空");
        Asserts.whenNull(status).throwIllegalArgumentException("状态不能为空");
    }
}
