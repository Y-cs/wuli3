package com.kjs.wuli3.permission.model;

/**
 * 业务资源枚举的权限定义契约；实现应返回固定元数据，不访问外部服务。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
public interface PermissionCode {
    /** 返回权限动作；同一资源内不可重复。 */
    ActionCode getAction();

    /** 返回权限业务描述；必须非空白。 */
    String getDescription();
}
