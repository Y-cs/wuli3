package com.kjs.wuli3.rocket.transaction;

/**
 * 按事件类型执行 RocketMQ 事务消息的本地事务，并支持 broker 回查。
 *
 * <p>实现注册为 Spring Bean 后自动参与事务消息处理；同一 eventType 只允许一个实现，重复注册启动失败。
 *
 * @author lipt create on 2026/11/9 10:57
 */
public interface RocketLocalTransaction {

    /**
     * 匹配的事件类型，必须与事件信封的 eventType 完全一致。
     *
     * @return 事件类型
     */
    String eventType();

    /**
     * 半消息发送成功后执行本地事务。
     *
     * <p>注意：抛出异常等价于返回 {@link RocketLocalTransactionState#UNKNOWN}，由 broker 后续回查决定提交或回滚。
     *
     * @param event 事务消息内容
     * @return 本地事务结果
     */
    RocketLocalTransactionState execute(RocketTransactionEvent event);

    /**
     * broker 回查本地事务状态。
     *
     * <p>注意：回查时没有发送时的业务参数，只能依据事件内容和本地持久化状态判断。
     *
     * @param event 事务消息内容
     * @return 本地事务结果
     */
    RocketLocalTransactionState check(RocketTransactionEvent event);
}
