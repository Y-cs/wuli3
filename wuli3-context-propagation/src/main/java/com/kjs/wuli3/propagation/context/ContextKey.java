package com.kjs.wuli3.propagation.context;

import lombok.Data;

/**
 * ContextKey
 * @author GuoYang create on 2026/9/28 14:45
 */
@Data
public class ContextKey<T extends Context> {

    private final Class<T> type;

    protected ContextKey(final Class<T> type) {
        this.type = type;
    }

    public static <T extends Context> ContextKey<T> of(final Class<T> type) {
        return new ContextKey<>(type);
    }


    /** 判断该类型是否属于可跨边界传播的上下文。 */
    public boolean isPropagatable() {
        return PropagationContext.class.isAssignableFrom(this.type);
    }

    /** 将上下文转换为当前 Key 声明的具体类型。 */
    public T cast(final Context context) {
        return this.type.cast(context);
    }
}
