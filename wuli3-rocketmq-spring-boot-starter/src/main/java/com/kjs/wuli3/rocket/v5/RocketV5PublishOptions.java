package com.kjs.wuli3.rocket.v5;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.event.PublishOptions;
import com.kjs.wuli3.event.options.AsyncPublishOptions;
import com.kjs.wuli3.event.options.TransactionalPublishOptions;
import java.time.Duration;
import java.util.function.BiConsumer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.jspecify.annotations.Nullable;

/** RocketMQ Java Client v5 事件传输支持的不可变发布选项。
 *
 * <p>多字段配置使用 {@link #builder()}，避免连续复制选项；固定配置可构造一次后复用。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record RocketV5PublishOptions(
        boolean async,
        boolean afterCommit,
        @Nullable Duration delay,
        @Nullable String orderKey,
        @Nullable BiConsumer<@Nullable SendReceipt, @Nullable Throwable> sendCallback)
        implements AsyncPublishOptions, TransactionalPublishOptions, PublishOptions {

    /** 创建同步、立即投递的默认选项。 */
    public RocketV5PublishOptions() {
        this(false, false, null, null, null);
    }

    /** 校验发布选项中的通用约束。 */
    public RocketV5PublishOptions {
        Asserts.whenTrue(delay != null && (delay.isZero() || delay.isNegative()))
                .throwIllegalArgumentException("delay must be positive");
        Asserts.whenTrue(orderKey != null && orderKey.isBlank())
                .throwIllegalArgumentException("orderKey cannot be blank");
    }

    /** 创建默认同步、立即发送的构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 在可变构建器中组合配置，仅在 build 时创建不可变选项。
     *
     * <p>注意：构建器不支持并发使用；构建后的选项不受后续构建器修改影响。
     *
     * @author GuoYang create on 2026/9/30 10:00
     */
    public static final class Builder {
        private boolean async;
        private boolean afterCommit;
        private @Nullable Duration delay;
        private @Nullable String orderKey;
        private @Nullable BiConsumer<@Nullable SendReceipt, @Nullable Throwable> sendCallback;

        private Builder() {}

        /** 设置是否异步发送。 */
        public Builder async(final boolean async) {
            this.async = async;
            return this;
        }

        /** 设置是否在事务提交后发送。 */
        public Builder afterCommit(final boolean afterCommit) {
            this.afterCommit = afterCommit;
            return this;
        }

        /** 设置精确延迟；null 表示不延迟，正值约束在 build 时校验。 */
        public Builder delay(final @Nullable Duration delay) {
            this.delay = delay;
            return this;
        }

        /** 设置顺序键；null 表示非顺序发送，非空白约束在 build 时校验。 */
        public Builder orderKey(final @Nullable String orderKey) {
            this.orderKey = orderKey;
            return this;
        }

        /**
         * 设置发送完成回调；成功参数为回执和 null，失败参数为 null 和异常。
         *
         * <p>同步发送在调用线程通知，失败通知后仍抛出发送异常；异步发送在完成线程通知。
         * 参数校验和异步启动失败直接抛异常，不通知回调。回调异常只记录日志，不改变发送结果。
         * 批量中的每条消息共用此回调，调用方负责线程安全；null 使用默认异步失败日志。
         */
        public Builder sendCallback(
                final @Nullable BiConsumer<@Nullable SendReceipt, @Nullable Throwable> sendCallback) {
            this.sendCallback = sendCallback;
            return this;
        }

        /** 校验配置并创建独立的不可变选项。 */
        public RocketV5PublishOptions build() {
            return new RocketV5PublishOptions(
                    this.async, this.afterCommit, this.delay, this.orderKey, this.sendCallback);
        }
    }
}
