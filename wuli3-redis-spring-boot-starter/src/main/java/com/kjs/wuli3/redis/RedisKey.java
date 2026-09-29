package com.kjs.wuli3.redis;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 同时表达 key 值和稳定过期策略的 Redis key。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record RedisKey(String value, Optional<Duration> timeToLive) {

    private static final Duration MINIMUM_TIME_TO_LIVE = Duration.ofMillis(1);

    public RedisKey {
        Asserts.whenBlank(value).throwIllegalArgumentException("Redis key must not be blank");
        Asserts.whenNull(timeToLive).throwIllegalArgumentException("timeToLive must not be null");
        timeToLive.ifPresent(RedisKey::validateTimeToLive);
    }

    /** 创建不会自动过期的 key。 */
    public static RedisKey persistent(final String value) {
        return new RedisKey(value, Optional.empty());
    }

    /** 创建在指定持续时间后过期的 key。 */
    public static RedisKey expiring(final String value, final Duration timeToLive) {
        return new RedisKey(value, Optional.of(timeToLive));
    }

    private static void validateTimeToLive(final Duration timeToLive) {
        final long timeToLiveMillis;
        try {
            timeToLiveMillis = timeToLive.toMillis();
        } catch (ArithmeticException exception) {
            throw new ErrorCodeException(
                    CommonErrors.ILLEGAL_ARGUMENT,
                    "Redis key timeToLive is too large", exception);
        }
        if (timeToLiveMillis < RedisKey.MINIMUM_TIME_TO_LIVE.toMillis()) {
            Asserts.whenTrue(true).throwIllegalArgumentException("Redis key timeToLive must be at least 1 millisecond");
        }
    }
}
