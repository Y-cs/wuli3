package com.kjs.wuli3.permission.model;

import com.kjs.wuli3.core.assertion.Asserts;

/**
 * 绑定认证身份的可轮换权限主体码；由可信入口提供，业务服务不得自行推导。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
public record PermissionToken(String token) {
    /** 校验业务输入的主体标识。 */
    public PermissionToken {
        Asserts.whenBlank(token).throwIllegalArgumentException("权限主体码不能为空");
    }

    public static PermissionToken of(final String code) {
        return new PermissionToken(code);
    }
}
