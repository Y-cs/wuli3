# 权限声明与鉴权

核心模块 `wuli3-permission` 不依赖 Spring，负责资源定义、本地资源存储、主体权限快照、引用解析和位图判断。
`wuli3-permission-spring-boot-starter` 负责发现资源类、启动校验、读取可信 Token、调用应用加载器和拦截方法。

## 声明资源

```java
@Getter
@RequiredArgsConstructor
@PermissionResource(value = "ORDER", description = "订单权限")
public enum OrderPermission implements PermissionCode {
    READ(ActionCode.READ, "查询订单"),
    APPROVE(ActionCode.ACTION_4, "审核订单");

    private final ActionCode action;
    private final String description;
}
```

`PermissionCode` 的方法为 `getAction()` 和 `getDescription()`。资源枚举的全部常量进入本地存储，包括尚未被方法使用的常量。
资源码与动作常量名只能由大写字母、数字和下划线组成，同一资源只能对应一个枚举，同一枚举动作不可重复。描述必填。
枚举只提供固定元数据，不应访问数据库或网络。

`ActionCode` 使用显式十进制位号和掩码：CRUD 占位 0–3，其他动作占位 4–63。
第 63 位掩码是 `Long.MIN_VALUE`；负数位图合法，不能使用 `ordinal()` 持久化动作。

## 两种存储

`store.LocalPermissionStore` 由扫描得到的资源枚举构建：

```java
final LocalPermissionStore local = new LocalPermissionStore(List.of(OrderPermission.class));
```

- `resources()` 提供资源描述、枚举类型及权限列表，供导出入库。
- `requirement()` 提供本服务全部资源的所需动作位图 `Map<String, Long>`，供加载主体权限。

`model.PrincipalPermissions` 表示用户服务返回的主体权限快照：

```java
final PrincipalPermissions principal = new PrincipalPermissions(
        token, Map.of("ORDER", ActionCode.READ.getMask()));
```

它绑定一个 `PermissionToken`，保存不可变的 `Map<String, Long>`。缺少资源或对应动作位即无权限。
本地资源存储和主体权限快照都不访问网络或数据库。

## 解析与代码鉴权

`PermissionResolver` 是静态工具，显式接收本地资源存储，三种引用方式统一得到 `PermissionKey`：

```java
final PermissionKey key = PermissionResolver.resolve(local, OrderPermission.APPROVE);
PermissionResolver.resolve(local, "ORDER:APPROVE", PermissionCode.class);
PermissionResolver.resolve(local, "APPROVE", OrderPermission.class);

// 纯 Java 使用主体快照进行位图判断。
final boolean allowed = principal.contains(key);
```

Spring 的 `PermissionChecker` 是统一业务入口，由容器注入：

```java
permissionChecker.require(OrderPermission.APPROVE);
final boolean allowed = permissionChecker.has(OrderPermission.READ);
permissionChecker.requireAll(List.of(key));
permissionChecker.requireAny(List.of(key));
```

每次调用读取当前可信 Token，通过核心 `PrincipalPermissionLoader` 获取该主体的权限；组合判断只加载一次。
`has` 在缺少 Token 时返回 false；`require`、`requireAll`、`requireAny` 缺少 Token 时抛 `UNAUTHORIZED`，缺少权限时抛 `FORBIDDEN`。
空权限集合属于非法输入。Checker 不绑定单个用户快照，可以作为单例 Bean 使用。
解析使用已注册的资源定义，带常量专属实现的枚举通过 `getDeclaringClass()` 识别。

## Spring 注解鉴权

```java
@Permission("ORDER:APPROVE")
public void approve() { /* 业务 */ }

@Permission(value = "APPROVE", resourceClass = OrderPermission.class)
public void approveAgain() { /* 业务 */ }
```

类、方法、接口与父类上的声明叠加生效，要求全部满足。
Spring 扫描器发现资源类，`LocalPermissionStore` 负责解析资源声明并建立本地目录；启动时验证注解引用并保存不可变的引用到权限键索引。
AOP 使用有界 Caffeine 缓存方法所需权限列表，缓存键包含目标类和方法；缓存未命中时只组合启动期已解析的权限键，不再调用字符串解析器。未登记的引用会明确失败，不回退到运行时解析；不缓存主体权限或鉴权结果。
公共 `PermissionResolver` 保留输入校验，供非 Spring 应用及直接代码调用使用。

应用提供 `spi.PrincipalPermissionLoader`：

```java
@Bean
PrincipalPermissionLoader principalPermissionLoader(final UserPermissionClient client) {
    return (token, requiredResources) -> new PrincipalPermissions(
            token, client.load(token.token(), requiredResources));
}
```

默认 Spring 目录使用 `application.service.service-code` 作为服务标识，未配置或为空白时回退到 `spring.application.name`；两者均为空时启动失败。该标识由 `wuli3-core-spring-boot-starter` 的 `com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties` 统一绑定，也供 Web 错误码及 Redis 前缀使用。

例如服务标识为 `order-service` 时，`resources()`、`requirement()` 和最终 `PermissionKey.resourceCode()` 都使用 `order-service:ORDER`。`@PermissionResource("ORDER")`、`@Permission("ORDER:APPROVE")` 和枚举调用仍使用本地声明，不手动拼服务码。用户服务返回的授权 Map 必须使用相同的完整资源码；`ORDER` 或其他服务的同名资源不会匹配。直接构造 `PermissionKey` 时须提供完整资源码，推荐用 `PermissionResolver.resolve(store, enumConstant)` 从目录取得。

升级需迁移既有权限目录与授权数据的资源码。自定义目录应显式调用 `new LocalPermissionStore(serviceCode, resourceTypes)`；单参数构造保留纯 Java 的无前缀行为，Spring 不会重写应用提供的目录。

`UserPermissionClient` 是业务接口。加载参数包含可信上下文的 `PermissionToken` 和 `LocalPermissionStore.requirement()`。
应用接入层负责 Redis 查询、未命中回源用户服务、回填、TTL 和撤权失效；用户服务负责授权管理。
缓存键必须区分服务、Token 和资源范围。空 Map 表示成功查询但无权限，不等同于缓存未命中。

AOP 和代码调用共用 Spring 的 `PermissionChecker`，每次鉴权重新取得当前 Token 并调用加载器。没有 Token 抛 `UNAUTHORIZED`，加载异常原样传播，返回快照必须匹配当前 Token。
Token 来自 `AuthContext.principalPermissionToken`，HTTP 头为 `X-Wuli3-Principal-Permission-Token`。
轮换 Token 后，在途请求和旧消息的失效策略由接入层负责。

```yaml
wuli3:
  permission:
    base-packages:
      - com.example.order
      - com.example.shared.permissions
    enforcement-enabled: true
```

默认扫描应用根包及 Bean 声明；显式引用的外部枚举会纳入目录。
只用于代码调用的外部枚举需加入扫描包或自定义 `LocalPermissionStore` Bean。
设置 `enforcement-enabled: false` 仅保留资源目录，不再注册 `PermissionChecker` 和 Advisor，无需权限加载器或上下文读取器。纯 Java 的 `PrincipalPermissions.contains()` 不受影响。

启用鉴权必须提供 `PrincipalPermissionLoader` 和 `ContextReader`。Web/Dubbo starter 已配置上下文后端时应复用；独立 Spring 应用可注册：

```java
@Bean
ThreadLocalContextBackend contextBackend() {
    return new ThreadLocalContextBackend();
}
```

该对象同时实现 `ContextReader` 和 `ContextBinder`，可信入口必须使用同一对象绑定上下文，不能另外创建一份供 Checker 读取。

权限实现类必须能通过扫描包或 Bean 的具体声明类型发现。懒加载 Bean 或 FactoryBean 若仅暴露接口/Object，应将实际实现包加入 `base-packages`；该配置替换默认扫描包，需同时列出应用包。启动后不动态补录资源或声明：未登记的声明在进入权限拦截器时拒绝执行，不回退到运行时解析。

自定义 `LocalPermissionStore` 替换默认资源目录，不会自动合并扫描资源。直接构造的 `PermissionKey` 也应由本地目录的资源和动作范围覆盖；Checker 向 Loader 请求的范围始终来自 `LocalPermissionStore.requirement()`，不会因传入额外 Key 自动扩大。

权限模块仅拦截 Spring Bean 的 public 实例方法，不检测 Bean 是否满足 AOP 代理条件，由使用方保证调用经过代理。支持 JDK/CGLIB、接口、父类和泛型桥接。

- CGLIB 不能代理 final 类，也不能拦截 final 方法；JDK 代理通过接口调用，不受实现方法 final 修饰的同等限制。
- private、static 方法不适用注解鉴权；同类自调用和手动 new 不经过 Spring 代理。
- 不经过拦截器的调用不会执行权限检查，也不会触发未登记声明检查；需要在这些路径鉴权时，直接调用 `PermissionChecker`。
- FactoryBean、懒加载及自定义代理同样遵守上述约束，模块不安装全局 Bean 校验后处理器。
- 与 `@Async` 组合时应向执行线程传播可信上下文；自定义更早执行的环绕切面若直接返回，也会跳过后续权限检查。

## 模型和包

- `.model.definition`：资源与动作的声明元数据。
- `.store`：本地资源目录；`.model.PrincipalPermissions`：主体授权快照。
- 根包 `PermissionResolver`：静态解析工具，显式接收本地资源存储。
- 核心 `.spi.PrincipalPermissionLoader`：应用实现的权限加载扩展点。
- Starter 根包 `PermissionChecker`：读取当前上下文、加载权限、统一鉴权。
- Starter `.scan`：资源类发现；`.internal`：AOP 和方法元数据。

旧目录兼容入口、有状态判断器、独立权限范围模型及旧加载 SPI 已移除；调用方需迁移到上述 API。

## 验证

```bash
./gradlew :wuli3-permission:check :wuli3-permission-spring-boot-starter:check
```

## Higress 可信入口契约

全部标准字段使用 `X-Wuli3-` 前缀：`Request-Id`、`Origin-Ip`、`Principal-Type`、`Principal-Id`、`Principal-Permission-Token`。
Higress 对外部入站请求按大小写无关前缀删除所有 `X-Wuli3-*`（含未知后缀及重复值），再依据可信认证结果设置各字段的单值。
清洗必须先于认证结果注入；清洗规则覆盖所有外部路由。内部转发只在可信网络中保留和传播新头。
不增加 Trusted 标记，前缀不是签名；业务服务应禁止绕过网关的非可信访问。
这里是部署契约，不是假设某个 Higress 配置字段支持通配符；具体过滤插件和执行顺序需在网关仓库实现并验证。
服务端不兼容旧标准头，HTTP 认证头重复值拒绝解析；出站移除保留前缀后按当前快照重写。
来源 IP 仅在直接 peer 命中 `trusted-proxy-cidrs` 时读取 `X-Wuli3-Origin-Ip`；请求 ID 默认字段也已迁移。
升级需同时调整网关注入和服务协议；认证上下文仍可缺少主体码用于无需权限的业务，但受保护操作将拒绝。
