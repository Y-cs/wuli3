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


    public T cast(Context context) {
        return type.cast(context);
    }
}
