package com.wulicode.wuli3.propagation.internal;

import com.wulicode.wuli3.core.error.ErrorCodeException;
import com.wulicode.wuli3.core.error.builtin.SystemErrors;

/** 可在可信内部调用链中传播的认证主体类型。 */
public enum PrincipalType {
    CUSTOMER,
    ADMIN,
    SYSTEM;

    public static PrincipalType parse(String enumValue) {
        try {
            return PrincipalType.valueOf(enumValue);
        } catch (IllegalArgumentException ignored) {
            throw new ErrorCodeException(SystemErrors.ILLEGAL_ARGUMENT);
        }
    }
}
