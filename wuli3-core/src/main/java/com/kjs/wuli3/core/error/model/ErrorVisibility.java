package com.kjs.wuli3.core.error.model;

/** 定义错误码和错误消息的对外暴露范围。
 *
 * <p>该策略仅在最终公开边界应用，可信内部传播保留完整信息和本策略。
 * 与错误责任归属相互独立，不用于判断错误应由哪一方修复。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public enum ErrorVisibility {

    /** 对外输出错误码和错误消息。 */
    PUBLIC,
    /** 只对外输出错误码。 */
    CODE_ONLY,
    /** 只对外输出错误消息。 */
    MESSAGE_ONLY,
    /** 错误码和错误消息都不对外输出。 */
    INTERNAL
}
