package com.kjs.wuli3.permission.lifecyclefixture;

import com.kjs.wuli3.permission.annotation.Permission;
import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;

/**
 * 隔离可扫描的权限实现类，验证容器声明类型无法暴露实现类时的接入边界。
 *
 * @author 国杨 create on 2026/10/14 10:00
 */
public final class LifecycleFixture {
    private LifecycleFixture() {}

    @PermissionResource(value = "LIFECYCLE", description = "生命周期测试权限")
    public enum ResourcePermission implements PermissionCode {
        READ;

        @Override
        public ActionCode getAction() {
            return ActionCode.READ;
        }

        @Override
        public String getDescription() {
            return "读取";
        }
    }

    public interface Contract {
        String read();
    }

    public static class ProtectedService implements Contract {
        @Override
        @Permission("LIFECYCLE:READ")
        public String read() {
            return "protected";
        }
    }
}
