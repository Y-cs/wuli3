package com.kjs.wuli3.aliyun.error;

import com.kjs.wuli3.core.error.ErrorCodeException;
import java.io.Serial;

/**
 * STS 操作失败异常。
 *
 * @author lipt create on 2026/11/9 12:00
 */
public class StsOperateException extends ErrorCodeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public StsOperateException(String message) {
        super(StsErrorCode.OPERATE_ERROR, message);
    }

    public StsOperateException(String message, Throwable cause) {
        super(StsErrorCode.OPERATE_ERROR, message, cause);
    }

    public StsOperateException(Throwable cause) {
        super(StsErrorCode.OPERATE_ERROR, cause);
    }
}
