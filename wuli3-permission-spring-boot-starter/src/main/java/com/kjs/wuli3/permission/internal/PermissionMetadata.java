package com.kjs.wuli3.permission.internal;

import com.kjs.wuli3.permission.annotation.Permission;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;

/**
 * 提取和拦截共用的权限解析规则；继承、接口、类型及方法声明叠加生效。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
public final class PermissionMetadata {
    private PermissionMetadata() {}
    /** 返回类型层次内的全部声明，用于目录提取。 */
    public static List<Permission> declarations(final Class<?> type) {
        // 目录扫描需要读取类型、接口和父类上的全部声明。
        return PermissionMetadata.collect(type, method -> true);
    }

    /** 返回一次方法调用所需全部权限；方法不能覆盖并取消类型要求。 */
    public static List<Permission> requirements(final Class<?> type, final Method invoked) {
        // 桥接方法先还原到业务方法，避免泛型接口的权限声明丢失或重复。
        final Method specific = BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(invoked, type));
        return PermissionMetadata.collect(
                type,
                method -> BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, type))
                        .equals(specific));
    }

    private static List<Permission> collect(final Class<?> type, final Predicate<Method> matches) {
        // 统一遍历层次结构，保证扫描和运行时拦截使用完全相同的合并规则。
        final List<Permission> result = new ArrayList<>();
        for (final Class<?> owner : PermissionMetadata.hierarchy(type)) {
            result.addAll(PermissionMetadata.read(owner));
            for (final Method method : owner.getDeclaredMethods()) {
                if (!method.isBridge() && !method.isSynthetic() && matches.test(method)) {
                    result.addAll(PermissionMetadata.read(method));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<Permission> read(final AnnotatedElement element) {
        return List.of(element.getAnnotationsByType(Permission.class));
    }

    private static Set<Class<?>> hierarchy(final Class<?> type) {
        final Set<Class<?>> types = new LinkedHashSet<>();
        PermissionMetadata.visit(type, types);
        return types;
    }

    private static void visit(final Class<?> type, final Set<Class<?>> types) {
        if (type == Object.class || !types.add(type)) {
            return;
        }
        for (final Class<?> contract : type.getInterfaces()) {
            PermissionMetadata.visit(contract, types);
        }
        final Class<?> parent = type.getSuperclass();
        if (parent != null) {
            PermissionMetadata.visit(parent, types);
        }
    }
}
