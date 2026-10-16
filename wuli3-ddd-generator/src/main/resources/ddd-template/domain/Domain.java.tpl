package {{basePackage}}.domain.{{domainPackage}};

import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;
import com.kjs.wuli3.core.assertion.Asserts;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * {{domainType}} 聚合根示例。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
@Getter
@Accessors(fluent = true)
public final class {{domainType}} {
    private final EntityId id;
    private Status status;

    /** 创建处于启用状态的聚合。 */
    public {{domainType}}(final EntityId id) {
        this(id, Status.ACTIVE);
    }

    /** 从已保存的标识与状态还原聚合。 */
    public {{domainType}}(final EntityId id, final Status status) {
        Asserts.whenNull(id).throwIllegalArgumentException("标识不能为空");
        Asserts.whenNull(status).throwIllegalArgumentException("状态不能为空");
        this.id = id;
        this.status = status;
    }

    /** 停用当前聚合。 */
    public void deactivate() {
        this.status = Status.INACTIVE;
    }
}
