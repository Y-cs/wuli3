package com.kjs.wuli3.permission.model;

import com.kjs.wuli3.core.assertion.Asserts;

/**
 * 资源编码和动作位序号组成的唯一权限标识。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
public record PermissionKey(String resourceCode, ActionCode actionCode) {
    /** 校验外部传入的资源与动作。 */
    public PermissionKey {
        Asserts.whenBlank(resourceCode).throwIllegalArgumentException("权限资源编码不能为空");
        Asserts.whenNull(actionCode).throwIllegalArgumentException("权限动作不能为空");
    }
    /** 判断授权位图是否包含该动作，包括 long 符号位。 */
    public boolean matches(final long grantedMask) {
        final long mask = this.actionCode.getMask();
        return (grantedMask & mask) == mask;
    }
}
