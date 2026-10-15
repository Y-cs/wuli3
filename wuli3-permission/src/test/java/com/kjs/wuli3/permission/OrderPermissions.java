package com.kjs.wuli3.permission;

import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;

/** 测试全部 64 个动作的资源定义。
 * @author 国杨 create on 2026/10/13 10:00
 */
@PermissionResource(value = "ORDER", description = "订单")
enum OrderPermissions implements PermissionCode {
    CREATE,
    DELETE,
    UPDATE,
    READ,
    ACTION_4,
    ACTION_5,
    ACTION_6,
    ACTION_7,
    ACTION_8,
    ACTION_9,
    ACTION_10,
    ACTION_11,
    ACTION_12,
    ACTION_13,
    ACTION_14,
    ACTION_15,
    ACTION_16,
    ACTION_17,
    ACTION_18,
    ACTION_19,
    ACTION_20,
    ACTION_21,
    ACTION_22,
    ACTION_23,
    ACTION_24,
    ACTION_25,
    ACTION_26,
    ACTION_27,
    ACTION_28,
    ACTION_29,
    ACTION_30,
    ACTION_31,
    ACTION_32,
    ACTION_33,
    ACTION_34,
    ACTION_35,
    ACTION_36,
    ACTION_37,
    ACTION_38,
    ACTION_39,
    ACTION_40,
    ACTION_41,
    ACTION_42,
    ACTION_43,
    ACTION_44,
    ACTION_45,
    ACTION_46,
    ACTION_47,
    ACTION_48,
    ACTION_49,
    ACTION_50,
    ACTION_51,
    ACTION_52,
    ACTION_53,
    ACTION_54,
    ACTION_55,
    ACTION_56,
    ACTION_57,
    ACTION_58,
    ACTION_59,
    ACTION_60,
    ACTION_61,
    ACTION_62,
    ACTION_63;

    /** 返回对应动作。 */
    @Override
    public ActionCode getAction() {
        return ActionCode.valueOf(this.name());
    }

    /** 返回测试描述。 */
    @Override
    public String getDescription() {
        return this.name();
    }
}
