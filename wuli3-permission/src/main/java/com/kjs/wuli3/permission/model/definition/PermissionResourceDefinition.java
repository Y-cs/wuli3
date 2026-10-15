package com.kjs.wuli3.permission.model.definition;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.permission.model.PermissionCode;
import java.util.List;
import java.util.Objects;

/**
 * 资源及其完整权限声明；枚举类型仅用于本地引用解析，不属于持久化字段。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
public record PermissionResourceDefinition(
        String resourceCode,
        String description,
        Class<? extends PermissionCode> enumClass,
        List<PermissionActionDefinition> permissions) {
    /** 校验资源元数据并防御性复制权限列表；枚举声明一致性由目录构建时检查。 */
    public PermissionResourceDefinition {
        Asserts.whenBlank(resourceCode).throwIllegalArgumentException("权限资源编码不能为空");
        Asserts.whenFalse(resourceCode.matches("[A-Z0-9_]+")).throwIllegalArgumentException("权限资源编码只能由大写字母、数字和下划线组成");
        Asserts.whenBlank(description).throwIllegalArgumentException("资源描述不能为空");
        Asserts.whenNull(enumClass).throwIllegalArgumentException("权限枚举类型不能为空");
        Asserts.whenFalse(enumClass.isEnum() && PermissionCode.class.isAssignableFrom(enumClass))
                .throwIllegalArgumentException("权限声明类型必须是权限枚举");
        Asserts.whenNull(permissions).throwIllegalArgumentException("资源权限列表不能为空");
        Asserts.whenTrue(permissions.stream().anyMatch(Objects::isNull))
                .throwIllegalArgumentException("资源权限列表不能包含 null");
        permissions = List.copyOf(permissions);
    }
}
