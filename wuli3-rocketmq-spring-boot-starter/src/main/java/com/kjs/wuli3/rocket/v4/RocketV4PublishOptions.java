package com.kjs.wuli3.rocket.v4;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.event.PublishOptions;
import com.kjs.wuli3.event.options.AsyncPublishOptions;
import com.kjs.wuli3.event.options.TransactionalPublishOptions;
import java.time.Duration;
import org.apache.rocketmq.client.producer.SendCallback;
import org.jspecify.annotations.Nullable;

/** RocketMQ 事件传输支持的不可变发布选项。
 *
 * <p>多字段配置使用 {@link #builder()}，避免连续复制选项；固定配置可构造一次后复用。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record RocketV4PublishOptions(
        boolean async,
        boolean afterCommit,
        @Nullable Duration delay,
        @Nullable String orderKey,
        @Nullable SendCallback sendCallback)
        implements AsyncPublishOptions, TransactionalPublishOptions, PublishOptions {

    /** 创建同步、立即投递的默认选项。 */
    public RocketV4PublishOptions() {
        this(false, false, null, null, null);
    }

    /** 校验发布选项中的通用约束。 */
    public RocketV4PublishOptions {
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
        private @Nullable SendCallback sendCallback;

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

        /** 设置 v4 同步或异步发送的原生回调。
         *
         * <p>批量发送的每条消息共用此回调，线程安全与异常处理由调用方负责。
         * null 使用默认失败日志；自定义回调接管成功与失败处理。
         * 同步回调在发送线程执行；回调运行时异常记录日志，不改变发送结果。
         * 异步启动或参数校验失败直接抛异常，不额外触发回调。 */
        public Builder sendCallback(final @Nullable SendCallback sendCallback) {
            this.sendCallback = sendCallback;
            return this;
        }

        /** 校验配置并创建独立的不可变选项。 */
        public RocketV4PublishOptions build() {
            return new RocketV4PublishOptions(
                    this.async, this.afterCommit, this.delay, this.orderKey, this.sendCallback);
        }
    }
}
