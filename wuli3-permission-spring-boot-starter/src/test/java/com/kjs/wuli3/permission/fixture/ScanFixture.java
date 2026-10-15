package com.kjs.wuli3.permission.fixture;

import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 验证未被业务方法引用的权限也进入服务目录。
 * @author 国杨 create on 2026/10/13 10:00
 */
@Getter
@RequiredArgsConstructor
@PermissionResource(value = "SCAN", description = "扫描资源")
public enum ScanFixture implements PermissionCode {
    READ(ActionCode.READ, "读取"),
    HIGH(ActionCode.ACTION_63, "特殊操作");
    private final ActionCode actionCode;
    private final String description;

    @Override
    public ActionCode getAction() {
        return this.actionCode;
    }
}
