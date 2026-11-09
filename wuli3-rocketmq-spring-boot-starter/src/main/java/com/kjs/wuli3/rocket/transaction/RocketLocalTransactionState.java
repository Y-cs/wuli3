package com.kjs.wuli3.rocket.transaction;

/**
 * 本地事务结果，决定事务消息提交、回滚或等待 broker 回查。
 *
 * @author lipt create on 2026/11/9 10:57
 */
public enum RocketLocalTransactionState {

    /** 提交半消息，对消费者可见。 */
    COMMIT,

    /** 回滚半消息，消费者永远不可见。 */
    ROLLBACK,

    /** 本地事务状态暂不确定，由 broker 后续回查决定。 */
    UNKNOWN
}
