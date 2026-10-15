package com.kjs.wuli3.permission.store;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.error.PermissionErrors;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.model.definition.PermissionActionDefinition;
import com.kjs.wuli3.permission.model.definition.PermissionResourceDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** 保存当前服务扫描得到的完整权限资源声明。
 *
 * @author 国杨 create on 2026/10/13 10:00
 */
public final class LocalPermissionStore {
    private static final Pattern UPCASE_NUMBER_PATTERN = Pattern.compile("[A-Z0-9_]+");
    private final List<PermissionResourceDefinition> resources;
    private final Map<String, PermissionResourceDefinition> byCode;
    private final Map<Class<?>, PermissionResourceDefinition> byType;
    private final Map<String, Long> requirement;
    private final Map<String, PermissionKey> keys;

    /** 校验资源枚举并一次性构建不可变资源和权限索引。 */
    public LocalPermissionStore(final Collection<Class<?>> resourceTypes) {
        Asserts.whenNull(resourceTypes).throwIllegalArgumentException("权限资源类型不能为空");

        final Map<String, PermissionResourceDefinition> codes = new TreeMap<>();
        final Map<Class<?>, PermissionResourceDefinition> types = new HashMap<>();
        for (final Class<?> type : resourceTypes) {
            Asserts.whenNull(type).throwIllegalArgumentException("权限资源类型不能包含 null");
            if (types.containsKey(type)) {
                continue;
            }
            final PermissionResourceDefinition definition = LocalPermissionStore.read(type);
            Asserts.whenTrue(codes.putIfAbsent(definition.resourceCode(), definition) != null)
                    .throwException(PermissionErrors.DECLARATION_CONFLICT, "资源编码重复：" + definition.resourceCode());
            types.put(type, definition);
        }
        this.resources = List.copyOf(codes.values());
        this.byCode = Map.copyOf(codes);
        this.byType = Map.copyOf(types);

        final Map<String, Long> masks = new TreeMap<>();
        this.resources.forEach(resource -> resource.permissions()
                .forEach(permission -> masks.merge(
                        resource.resourceCode(), permission.actionCode().getMask(), (left, right) -> left | right)));
        this.requirement = Map.copyOf(masks);

        final Map<String, PermissionKey> indexed = new HashMap<>();
        this.resources.forEach(resource -> resource.permissions()
                .forEach(permission -> indexed.put(
                        resource.resourceCode() + ":" + permission.name(),
                        new PermissionKey(resource.resourceCode(), permission.actionCode()))));
        this.keys = Map.copyOf(indexed);
    }

    /** 返回预先构建的规范权限键；未知引用返回空。 */
    public @Nullable PermissionKey key(final String reference) {
        Asserts.whenBlank(reference).throwIllegalArgumentException("权限引用不能为空");
        return this.keys.get(reference);
    }

    /** 返回完整资源声明，供展示或入库使用。 */
    public List<PermissionResourceDefinition> resources() {
        return this.resources;
    }
    /** 返回需向用户服务拉取的资源和动作范围。 */
    public Map<String, Long> requirement() {
        return this.requirement;
    }
    /** 按资源编码查找声明；未注册返回空。 */
    public @Nullable PermissionResourceDefinition resource(final String code) {
        Asserts.whenBlank(code).throwIllegalArgumentException("资源编码不能为空");
        return this.byCode.get(code);
    }
    /** 按声明枚举类型查找资源；未注册返回空。 */
    public @Nullable PermissionResourceDefinition resource(final Class<?> type) {
        Asserts.whenNull(type).throwIllegalArgumentException("资源类型不能为空");
        return this.byType.get(type);
    }

    private static PermissionResourceDefinition read(final Class<?> type) {
        final String locationClass = type.getName();
        Asserts.whenFalse(type.isEnum() && PermissionCode.class.isAssignableFrom(type))
                .throwException(PermissionErrors.INVALID_DECLARATION, "资源须为权限枚举：" + locationClass);

        final PermissionResource annotation = type.getDeclaredAnnotation(PermissionResource.class);
        Asserts.whenNull(annotation)
                .throwException(PermissionErrors.INVALID_DECLARATION, "资源枚举缺少 PermissionResource：" + locationClass);
        final String resourceCode = annotation.value();
        Asserts.whenBlank(resourceCode)
                .throwException(PermissionErrors.INVALID_DECLARATION, "资源编码不能为空：" + locationClass);
        Asserts.whenFalse(UPCASE_NUMBER_PATTERN.matcher(resourceCode).matches())
                .throwException(PermissionErrors.INVALID_DECLARATION, "资源编码只能由大写字母、数字和下划线组成：" + locationClass);
        final String resourceDescription = annotation.description();
        Asserts.whenBlank(resourceDescription)
                .throwException(PermissionErrors.INVALID_DECLARATION, "资源描述不能为空：" + locationClass);

        final EnumSet<ActionCode> actions = EnumSet.noneOf(ActionCode.class);
        final List<PermissionActionDefinition> permissions = new ArrayList<>();
        for (final Object value : Objects.requireNonNull(type.getEnumConstants())) {
            final PermissionCode code = (PermissionCode) value;
            final String actionName = ((Enum<?>) value).name();
            Asserts.whenFalse(UPCASE_NUMBER_PATTERN.matcher(actionName).matches())
                    .throwException(PermissionErrors.INVALID_DECLARATION, "权限动作只能由大写字母、数字和下划线组成：" + locationClass);
            final String source = locationClass + "." + actionName;
            final ActionCode action = code.getAction();
            final String actionDescription = code.getDescription();
            Asserts.whenNull(action).throwException(PermissionErrors.INVALID_DECLARATION, "权限动作不能为空：" + source);
            Asserts.whenBlank(actionDescription)
                    .throwException(PermissionErrors.INVALID_DECLARATION, "权限描述不能为空：" + source);
            Asserts.whenFalse(actions.add(action))
                    .throwException(PermissionErrors.DECLARATION_CONFLICT, "同资源动作重复：" + source);
            permissions.add(new PermissionActionDefinition(actionName, action, actionDescription));
        }
        return new PermissionResourceDefinition(
                resourceCode, resourceDescription, type.asSubclass(PermissionCode.class), permissions);
    }
}
