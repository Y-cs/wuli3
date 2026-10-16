package {{basePackage}}.sharedkernel;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorModule;

/**
 * 示例用例的记录查询错误。
 *
 * @author GuoYang create on 2026/10/16 10:00
 */
@ErrorModule(name = "RECORD")
public enum RecordErrors implements ErrorCode {
    NOT_FOUND;

    /** 返回记录不存在的默认提示。 */
    @Override
    public String getMessage() {
        return "记录不存在";
    }
}
