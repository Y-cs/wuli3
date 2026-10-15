package com.kjs.wuli3.permission.internal;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.permission.annotation.Permission;
import com.kjs.wuli3.permission.error.PermissionErrors;
import com.kjs.wuli3.permission.model.PermissionKey;
import java.util.Map;
import java.util.Objects;

/**
 * 保存启动时编译的权限声明，运行时按注解内容读取不可变索引。
 *
 * @author 国杨 create on 2026/10/14 10:00
 */
public final class PermissionDeclarationIndex {
    private final Map<Permission, PermissionKey> permissions;

    /** 保存已解析的声明快照；扫描和解析由 Spring 启动流程完成。 */
    public PermissionDeclarationIndex(final Map<Permission, PermissionKey> permissions) {
        this.permissions = Map.copyOf(permissions);
    }

    /** 读取启动时编译的声明；拒绝未登记的运行时声明，不能绕过启动校验。 */
    public PermissionKey require(final Permission declaration) {
        final PermissionKey key = this.permissions.get(declaration);
        Asserts.whenNull(key).throwException(PermissionErrors.INVALID_DECLARATION, "权限声明未经过启动扫描: " + declaration);
        return Objects.requireNonNull(key);
    }
}
