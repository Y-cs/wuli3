package com.kjs.wuli3.rocket.internal;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.event.PublishOptions;
import com.kjs.wuli3.event.options.AsyncPublishOptions;
import com.kjs.wuli3.event.options.TransactionalPublishOptions;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** RocketMQ 事件传输支持的不可变发布选项。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record RocketPublishOptions(
        boolean async,
        boolean afterCommit,
        @Nullable Duration delay,
        @Nullable String orderKey) implements AsyncPublishOptions, TransactionalPublishOptions, PublishOptions {

    /** 创建同步、立即投递的默认选项。 */
    public RocketPublishOptions() {
        this(false, false, null, null);
    }

    /** 校验发布选项中的通用约束。 */
    public RocketPublishOptions {
        Asserts.whenTrue(delay != null && (delay.isZero() || delay.isNegative()))
                .throwIllegalArgumentException("delay must be positive");
        Asserts.whenTrue(orderKey != null && orderKey.isBlank())
                .throwIllegalArgumentException("orderKey cannot be blank");
    }

    /** 返回启用异步发送的副本。 */
    public RocketPublishOptions withAsync() {
        return new RocketPublishOptions(true, this.afterCommit, this.delay, this.orderKey);
    }

    /** 返回要求事务提交后发送的副本。 */
    public RocketPublishOptions withAfterCommit() {
        return new RocketPublishOptions(this.async, true, this.delay, this.orderKey);
    }

    /** 返回设置了精确延迟的副本。 */
    public RocketPublishOptions withDelay(final Duration delay) {
        Asserts.whenNull(delay).throwIllegalArgumentException("delay cannot be null");
        return new RocketPublishOptions(this.async, this.afterCommit, delay, this.orderKey);
    }

    /** 返回设置了顺序键的副本。 */
    public RocketPublishOptions withOrderKey(final String orderKey) {
        Asserts.whenNull(orderKey).throwIllegalArgumentException("orderKey cannot be null");
        return new RocketPublishOptions(this.async, this.afterCommit, this.delay, orderKey);
    }
}
