package com.kjs.wuli3.permission.spi;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.permission.model.PermissionToken;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import com.kjs.wuli3.permission.model.TtlPrincipalPermissions;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * AbstractPrincipalPermissionCacheLoader
 * @author GuoYang create on 2026/10/15 11:50
 */
public abstract class AbstractPrincipalPermissionCacheLoader implements PrincipalPermissionLoader {

    private static final long MAXIMUM_CACHE_SIZE = 10_000L;

    private final Cache<PermissionToken, TtlPrincipalPermissions> cache;

    /** 使用系统单调时钟创建本地权限缓存。 */
    protected AbstractPrincipalPermissionCacheLoader() {
        this(Ticker.systemTicker());
    }

    /** 使用指定时钟创建缓存，供同包测试验证 TTL。 */
    AbstractPrincipalPermissionCacheLoader(final Ticker ticker) {
        Asserts.whenNull(ticker).throwIllegalArgumentException("缓存时钟不能为空");
        this.cache = Caffeine.newBuilder()
                .maximumSize(AbstractPrincipalPermissionCacheLoader.MAXIMUM_CACHE_SIZE)
                .expireAfter(new PrincipalPermissionsExpiry())
                .ticker(ticker)
                .build();
    }

    /** 返回指定 Token 和资源范围对应的权限快照；加载异常会原样传播且不会缓存失败结果。 */
    @Override
    public final PrincipalPermissions load(final PermissionToken token, final Map<String, Long> requiredResources) {
        Asserts.whenNull(token).throwIllegalArgumentException("权限主体 Token 不能为空");
        Asserts.whenNull(requiredResources).throwIllegalArgumentException("权限资源范围不能为空");
        return this.cache.get(token, ignored -> {
            final TtlPrincipalPermissions loaded = this.loadPrincipalPermission(token, requiredResources);
            if (loaded == null || !token.equals(loaded.token())) {
                throw new IllegalStateException("权限加载器必须返回当前 Token 的权限快照");
            }
            return loaded;
        });
    }

    /** 从具体数据源加载一个主体及资源范围的权限快照，并返回该快照的缓存有效期。 */
    protected abstract TtlPrincipalPermissions loadPrincipalPermission(
            PermissionToken token, Map<String, Long> requiredResources);

    @NullMarked
    private static final class PrincipalPermissionsExpiry implements Expiry<PermissionToken, TtlPrincipalPermissions> {

        @Override
        public long expireAfterCreate(
                final PermissionToken token, final TtlPrincipalPermissions value, final long currentTime) {
            return PrincipalPermissionsExpiry.toNanos(value.validTime());
        }

        @Override
        public long expireAfterUpdate(
                final PermissionToken token,
                final TtlPrincipalPermissions value,
                final long currentTime,
                final long currentDuration) {
            return PrincipalPermissionsExpiry.toNanos(value.validTime());
        }

        @Override
        public long expireAfterRead(
                final PermissionToken token,
                final TtlPrincipalPermissions value,
                final long currentTime,
                final long currentDuration) {
            return currentDuration;
        }

        private static long toNanos(final Duration duration) {
            try {
                return duration.toNanos();
            } catch (final ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
    }
}
