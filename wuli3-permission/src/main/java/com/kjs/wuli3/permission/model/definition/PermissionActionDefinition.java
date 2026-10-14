package com.kjs.wuli3.permission.model.definition;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.permission.model.ActionCode;

/**
 * 资源内的权限声明；名称对应业务枚举常量，动作决定实际权限位。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
public record PermissionActionDefinition(String name, ActionCode actionCode, String description) {
    /** 校验权限声明元数据。 */
    public PermissionActionDefinition {
        Asserts.whenBlank(name).throwIllegalArgumentException("权限常量名不能为空");
        Asserts.whenNull(actionCode).throwIllegalArgumentException("权限动作不能为空");
        Asserts.whenBlank(description).throwIllegalArgumentException("权限描述不能为空");
    }
}
