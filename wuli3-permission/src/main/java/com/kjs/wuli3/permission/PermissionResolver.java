package com.kjs.wuli3.permission;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.error.PermissionErrors;
import com.kjs.wuli3.permission.model.PermissionCode;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.model.definition.PermissionResourceDefinition;
import com.kjs.wuli3.permission.store.LocalPermissionStore;

/** 将字符串、资源枚举和枚举常量统一解析为权限键。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
public final class PermissionResolver {
    private PermissionResolver() {}

    /** 将字符串和可选枚举类型解析为本地已注册权限键。 */
    public static PermissionKey resolve(
            final LocalPermissionStore store, final String value, final Class<? extends PermissionCode> resourceClass) {
        Asserts.whenNull(store).throwIllegalArgumentException("本地权限存储不能为空");
        Asserts.whenBlank(value).throwException(PermissionErrors.INVALID_DECLARATION, "权限引用不能为空");
        Asserts.whenNull(resourceClass).throwIllegalArgumentException("权限资源类型不能为空");
        final PermissionResourceDefinition resource;
        final String name;
        if (resourceClass == PermissionCode.class) {
            final int separator = value.indexOf(':');
            Asserts.whenTrue(separator <= 0 || separator == value.length() - 1 || separator != value.lastIndexOf(':'))
                    .throwException(PermissionErrors.INVALID_DECLARATION, "权限引用须为资源编码:常量名：" + value);
            resource = store.resource(value.substring(0, separator));
            name = value.substring(separator + 1);
        } else {
            Asserts.whenTrue(value.indexOf(':') >= 0)
                    .throwException(PermissionErrors.INVALID_DECLARATION, "指定资源类型时仅允许常量名：" + value);
            resource = store.resource(resourceClass);
            name = value;
        }
        Asserts.whenNull(resource).throwException(PermissionErrors.INVALID_DECLARATION, "权限资源未注册：" + value);
        final PermissionResourceDefinition registered = java.util.Objects.requireNonNull(resource);
        final PermissionKey key = store.key(registered.resourceCode() + ":" + name);
        Asserts.whenNull(key).throwException(PermissionErrors.INVALID_DECLARATION, "权限引用不存在：" + value);
        return java.util.Objects.requireNonNull(key);
    }

    /** 将业务枚举常量解析为本地已注册权限键。 */
    public static PermissionKey resolve(final LocalPermissionStore store, final PermissionCode permission) {
        Asserts.whenNull(store).throwIllegalArgumentException("本地权限存储不能为空");
        Asserts.whenNull(permission).throwIllegalArgumentException("权限枚举不能为空");
        if (permission instanceof Enum<?> constant) {
            return PermissionResolver.resolve(
                    store, constant.name(), constant.getDeclaringClass().asSubclass(PermissionCode.class));
        }
        throw new ErrorCodeException(PermissionErrors.INVALID_DECLARATION, "权限定义须遵守枚举定义约束");
    }
}
