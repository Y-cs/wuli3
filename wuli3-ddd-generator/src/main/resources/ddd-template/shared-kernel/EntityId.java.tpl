package {{basePackage}}.sharedkernel;

import com.kjs.wuli3.core.assertion.Asserts;
import java.io.Serializable;

/**
 * 跨领域共享的实体标识值对象。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public record EntityId(String value) implements Serializable {
    /** 校验实体标识不能为空。 */
    public EntityId {
        Asserts.whenBlank(value).throwIllegalArgumentException("标识不能为空");
    }
}
