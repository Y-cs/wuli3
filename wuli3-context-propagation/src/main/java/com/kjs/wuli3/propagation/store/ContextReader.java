package com.kjs.wuli3.propagation.store;

import com.kjs.wuli3.propagation.context.Context;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextState;
import java.util.Optional;

/**
 * 提供当前执行中上下文的只读访问。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public interface ContextReader {
    /** 按键读取当前上下文，不存在时返回空值。 */
    <T extends Context> Optional<T> get(ContextKey<T> key);

    /** 读取当前完整状态。 */
    ContextState state();
}
