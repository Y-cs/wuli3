package com.kjs.wuli3.permission.error;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorMetadata;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;

/**
 * 权限判断与目录声明错误码。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@ErrorModule(
        name = "PERMISSION",
        defaultMetadata = @ErrorMetadata(origin = ErrorOrigin.CALLER, severity = ErrorSeverity.WARNING))
public enum PermissionErrors implements ErrorCode {
    UNAUTHORIZED("未认证"),
    FORBIDDEN("无权限"),
    @ErrorMetadata(origin = ErrorOrigin.SERVER)
    INVALID_DECLARATION("权限声明无效"),
    @ErrorMetadata(origin = ErrorOrigin.SERVER)
    DECLARATION_CONFLICT("权限声明冲突");
    private final String message;

    PermissionErrors(final String message) {
        this.message = message;
    }
    /** 返回默认错误消息。 */
    @Override
    public String getMessage() {
        return this.message;
    }
}
