package com.kjs.wuli3.propagation.store;

import com.kjs.wuli3.propagation.context.ContextState;
import java.util.concurrent.Callable;

/**
 * 将完整状态绑定到回调的执行期间，隔离底层存储的作用域机制。
 *
 * <p>实现必须在任务返回或抛出异常后恢复外层绑定；不执行传播筛选或状态合并。
 *
 * @author GuoYang create on 2026/9/28 15:00
 */
public interface ContextBinder {
    /** 在指定状态绑定期间执行任务。 */
    void run(ContextState state, Runnable task);

    /** 在指定状态绑定期间计算结果，并原样传播任务异常。 */
    <T> T call(ContextState state, Callable<T> task) throws Exception;
}
