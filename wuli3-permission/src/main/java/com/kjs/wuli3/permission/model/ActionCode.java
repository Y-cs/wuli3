package com.kjs.wuli3.permission.model;

import com.kjs.wuli3.core.assertion.Asserts;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 权限动作及其在 long 中的位位置；第 63 位作为普通权限位使用。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@Getter
@RequiredArgsConstructor
public enum ActionCode {
    CREATE(0, 1L),
    DELETE(1, 2L),
    UPDATE(2, 4L),
    READ(3, 8L),
    ACTION_4(4, 16L),
    ACTION_5(5, 32L),
    ACTION_6(6, 64L),
    ACTION_7(7, 128L),
    ACTION_8(8, 256L),
    ACTION_9(9, 512L),
    ACTION_10(10, 1024L),
    ACTION_11(11, 2048L),
    ACTION_12(12, 4096L),
    ACTION_13(13, 8192L),
    ACTION_14(14, 16384L),
    ACTION_15(15, 32768L),
    ACTION_16(16, 65536L),
    ACTION_17(17, 131072L),
    ACTION_18(18, 262144L),
    ACTION_19(19, 524288L),
    ACTION_20(20, 1048576L),
    ACTION_21(21, 2097152L),
    ACTION_22(22, 4194304L),
    ACTION_23(23, 8388608L),
    ACTION_24(24, 16777216L),
    ACTION_25(25, 33554432L),
    ACTION_26(26, 67108864L),
    ACTION_27(27, 134217728L),
    ACTION_28(28, 268435456L),
    ACTION_29(29, 536870912L),
    ACTION_30(30, 1073741824L),
    ACTION_31(31, 2147483648L),
    ACTION_32(32, 4294967296L),
    ACTION_33(33, 8589934592L),
    ACTION_34(34, 17179869184L),
    ACTION_35(35, 34359738368L),
    ACTION_36(36, 68719476736L),
    ACTION_37(37, 137438953472L),
    ACTION_38(38, 274877906944L),
    ACTION_39(39, 549755813888L),
    ACTION_40(40, 1099511627776L),
    ACTION_41(41, 2199023255552L),
    ACTION_42(42, 4398046511104L),
    ACTION_43(43, 8796093022208L),
    ACTION_44(44, 17592186044416L),
    ACTION_45(45, 35184372088832L),
    ACTION_46(46, 70368744177664L),
    ACTION_47(47, 140737488355328L),
    ACTION_48(48, 281474976710656L),
    ACTION_49(49, 562949953421312L),
    ACTION_50(50, 1125899906842624L),
    ACTION_51(51, 2251799813685248L),
    ACTION_52(52, 4503599627370496L),
    ACTION_53(53, 9007199254740992L),
    ACTION_54(54, 18014398509481984L),
    ACTION_55(55, 36028797018963968L),
    ACTION_56(56, 72057594037927936L),
    ACTION_57(57, 144115188075855872L),
    ACTION_58(58, 288230376151711744L),
    ACTION_59(59, 576460752303423488L),
    ACTION_60(60, 1152921504606846976L),
    ACTION_61(61, 2305843009213693952L),
    ACTION_62(62, 4611686018427387904L),
    ACTION_63(63, -9223372036854775808L);

    // 按显式位序号索引，不依赖枚举声明顺序或 ordinal。
    private static final Map<Integer, ActionCode> BY_BIT_INDEX = Arrays.stream(ActionCode.values())
            .collect(Collectors.toUnmodifiableMap(ActionCode::getBitIndex, Function.identity()));

    private final int bitIndex;
    private final long mask;

    /** 从外部持久化的位序号解析动作。 */
    public static ActionCode fromBitIndex(final int bitIndex) {
        Asserts.whenTrue(bitIndex < 0 || bitIndex > 63).throwIllegalArgumentException("权限动作位序号必须在 0 至 63 之间");
        // 范围已通过公共边界校验，合法位缺失属于内部枚举定义错误。
        return Objects.requireNonNull(ActionCode.BY_BIT_INDEX.get(bitIndex), "权限枚举位定义不完整");
    }
}
