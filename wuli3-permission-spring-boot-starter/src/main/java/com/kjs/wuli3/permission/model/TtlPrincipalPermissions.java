package com.kjs.wuli3.permission.model;

import com.kjs.wuli3.core.assertion.Asserts;
import java.time.Duration;
import java.util.Map;

/**
 * TtlPrincipalPermissions
 * @author GuoYang create on 2026/10/15 11:49
 */
public class TtlPrincipalPermissions extends PrincipalPermissions {

    private final Duration validTime;

    /** 创建不可变权限快照及其缓存有效期；零时长表示该结果不留在缓存中。 */
    public TtlPrincipalPermissions(
            final PermissionToken token, final Map<String, Long> permissions, final Duration validTime) {
        super(token, permissions);
        Asserts.whenNull(validTime).throwIllegalArgumentException("缓存有效期不能为空");
        Asserts.whenTrue(validTime.isNegative()).throwIllegalArgumentException("缓存有效期不能为负数");
        this.validTime = validTime;
    }

    /** 返回此快照自加载起的缓存有效期。 */
    public Duration validTime() {
        return this.validTime;
    }
}
