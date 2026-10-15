package com.kjs.wuli3.permission.spi;

import com.kjs.wuli3.permission.model.PermissionToken;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import java.util.Map;

/**
 * 根据可信权限 Token 和本地资源范围获取主体权限；缓存及用户服务访问由应用实现。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
@FunctionalInterface
public interface PrincipalPermissionLoader {
    /** 返回与传入 Token 绑定的权限快照；加载失败应抛出异常，不得回退到其他主体权限。 */
    PrincipalPermissions load(PermissionToken token, Map<String, Long> requiredResources);
}
