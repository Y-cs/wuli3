package com.kjs.wuli3.permission.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kjs.wuli3.permission.PermissionChecker;
import com.kjs.wuli3.permission.model.PermissionKey;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.NonNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.Ordered;

/**
 * 在方法调用时加载当前主体权限并鉴权；仅缓存注解解析结果，不缓存主体权限。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
public final class PermissionAdvisor {
    private PermissionAdvisor() {}

    /** 创建优先于事务执行的 Advisor；延迟取得依赖，避免代理基础设施提前初始化业务 Bean。 */
    public static DefaultPointcutAdvisor create(
            final Supplier<PermissionDeclarationIndex> declarations, final Supplier<PermissionChecker> checkers) {
        final StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(final Method method, final @NonNull Class<?> targetClass) {
                // 只拦截可被 Spring 代理的 public 实例方法，并提前过滤无权限方法。
                return Modifier.isPublic(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())
                        && method.getDeclaringClass() != Object.class
                        && !PermissionMetadata.requirements(targetClass, method).isEmpty();
            }
        };
        final Cache<MethodTarget, List<PermissionKey>> resolved =
                Caffeine.newBuilder().maximumSize(4096).build();
        final MethodInterceptor interceptor = invocation -> {
            // 目标类和方法共同作为缓存键，避免不同代理或重载方法混用解析结果。
            final Object target = Objects.requireNonNull(invocation.getThis(), "权限代理缺少目标对象");
            final MethodTarget method = new MethodTarget(AopUtils.getTargetClass(target), invocation.getMethod());
            final List<PermissionKey> requirements = Objects.requireNonNull(resolved.get(method, key -> {
                // 声明已在启动时校验；缓存淘汰后只重组已编译的权限，不再解析字符串。
                final PermissionDeclarationIndex index = declarations.get();
                return PermissionMetadata.requirements(key.type(), key.method()).stream()
                        .map(index::require)
                        .distinct()
                        .toList();
            }));
            // 缓存命中也要重新鉴权；主体快照的新鲜度由加载器的缓存策略保证。
            checkers.get().requireAll(requirements);
            return invocation.proceed();
        };
        final DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return advisor;
    }

    private record MethodTarget(Class<?> type, Method method) {}
}
