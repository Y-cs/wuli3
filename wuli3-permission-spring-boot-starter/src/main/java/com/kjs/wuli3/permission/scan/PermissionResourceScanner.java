package com.kjs.wuli3.permission.scan;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.permission.annotation.Permission;
import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.internal.PermissionMetadata;
import com.kjs.wuli3.permission.model.PermissionCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.util.ClassUtils;

/**
 * 发现应用包中的资源声明及显式引用；不构建或缓存权限存储。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
public final class PermissionResourceScanner {

    private final ClassLoader classLoader;

    /** 使用应用类加载器读取目录，扫描过程不初始化业务类。 */
    public PermissionResourceScanner(final ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /** 从候选类型发现资源枚举，并纳入注解显式引用的外部枚举；解析和校验交给本地存储。 */
    public Set<Class<?>> resources(final Collection<Class<?>> types) {
        // 这里只发现类型，不读取枚举常量；资源校验和索引构建由 LocalPermissionStore 完成。
        Asserts.whenNull(types).throwIllegalArgumentException("候选类型不能为空");
        Asserts.whenTrue(types.stream().anyMatch(Objects::isNull)).throwIllegalArgumentException("类型不能包含 null");
        final Set<Class<?>> resources = new LinkedHashSet<>();
        for (final Class<?> type : types) {
            if (type.getSuperclass() != null && type.getSuperclass().isEnum()) {
                continue;
            }
            if (type.isAnnotationPresent(PermissionResource.class)
                    || (PermissionCode.class.isAssignableFrom(type) && !type.isInterface())) {
                resources.add(type);
            }
            for (final Permission reference : PermissionMetadata.declarations(type)) {
                if (reference.resourceClass() != PermissionCode.class) {
                    resources.add(reference.resourceClass());
                }
            }
        }
        return Set.copyOf(resources);
    }

    /** 发现候选类型而不初始化；只有构建资源枚举目录时读取枚举常量。 */
    public Set<Class<?>> scanTypes(final String... basePackages) {
        // 使用元数据读取器发现类名，避免扫描阶段初始化业务类。
        Asserts.whenNull(basePackages).throwIllegalArgumentException("扫描包不能为空");
        Asserts.whenTrue(basePackages.length == 0).throwIllegalArgumentException("至少指定一个扫描包");
        final Set<Class<?>> types = new LinkedHashSet<>();
        final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(this.classLoader);
        final CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
        try {
            for (final String basePackage : basePackages) {
                Asserts.whenBlank(basePackage).throwIllegalArgumentException("不能扫描空包");
                Asserts.whenFalse(Arrays.stream(basePackage.split("\\.", -1))
                                .allMatch(part ->
                                        part.matches("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*")))
                        .throwIllegalArgumentException("扫描包必须是完整 Java 包名");
                final String path =
                        "classpath*:" + ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
                for (final Resource resource : resolver.getResources(path)) {
                    final String name = readers.getMetadataReader(resource)
                            .getClassMetadata()
                            .getClassName();
                    if (!name.endsWith("package-info") && !name.endsWith("module-info")) {
                        types.add(Class.forName(name, false, this.classLoader));
                    }
                }
            }
        } catch (final IOException | ClassNotFoundException | LinkageError failure) {
            throw new IllegalStateException("无法完整读取权限目录", failure);
        }
        return Set.copyOf(types);
    }
}
