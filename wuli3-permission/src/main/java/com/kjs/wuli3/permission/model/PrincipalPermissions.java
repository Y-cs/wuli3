package com.kjs.wuli3.permission.model;

import com.kjs.wuli3.core.assertion.Asserts;
import java.util.Map;

/** 保存用户服务返回的单个权限主体快照；不负责远程加载和缓存策略。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
public class PrincipalPermissions {
    private final PermissionToken token;
    private final Map<String, Long> permissions;

    /** 复制指定 Token 的授权位图，缺少资源或零位图表示没有权限。 */
    public PrincipalPermissions(final PermissionToken token, final Map<String, Long> permissions) {
        Asserts.whenNull(token).throwIllegalArgumentException("权限主体 Token 不能为空");
        Asserts.whenNull(permissions).throwIllegalArgumentException("主体权限不能为空");
        permissions.forEach((resource, mask) -> {
            Asserts.whenBlank(resource).throwIllegalArgumentException("权限资源不能为空");
            Asserts.whenNull(mask).throwIllegalArgumentException("权限位图不能为空");
        });
        this.token = token;
        this.permissions = Map.copyOf(permissions);
    }

    /** 返回快照所属主体 Token。 */
    public PermissionToken token() {
        return this.token;
    }
    /** 返回不可变授权位图。 */
    public Map<String, Long> permissions() {
        return this.permissions;
    }
    /** 判断快照是否包含指定资源动作。 */
    public boolean contains(final PermissionKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("权限标识不能为空");
        return key.matches(this.permissions.getOrDefault(key.resourceCode(), 0L));
    }
}
