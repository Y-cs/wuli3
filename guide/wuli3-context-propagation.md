# wuli3-context-propagation

上下文数据、当前执行存储、固定状态执行代理和协议转换分别管理。当前运行于 JDK 21，不启用预览 API。

## 包与职责

| 包 | 类型 | 职责 |
|---|---|---|
| `com.kjs.wuli3.propagation` | `ContextManager` | 读取当前状态、捕获传播快照、创建代理 |
| 同上 | `ContextProxy` | 持有确定的 State，通过 run/call/wrap 执行任务 |
| `.context` | `Context`、`ContextKey` | 不可变上下文值及类型安全的键 |
| `.context` | `ContextState` | 完整不可变集合；只管理数据 |
| `.context` | `ContextSnapshot`、`PropagationContext` | 跨边界传播的数据集合和传播资格 |
| `.store` | `ContextReader` | 读取当前生效的 State |
| `.store` | `ContextBinder`、`ThreadLocalContextBackend` | 回调期间建立绑定，结束后恢复；默认实现同时提供读取能力 |
| `.codec` | `ContextCodec`、`ContextPropagator` | Snapshot 与协议字段双向转换 |
| `.accessor` | 专用 accessor | 业务只读访问入口 |
| `.internal` | 内置身份、请求上下文及 Codec | 内置模型与字段契约 |

State 和 Snapshot 保存不可变值的引用，不做深拷贝。State 支持 with/without 创建新集合，不改变当前绑定。Snapshot.from(state) 仅保留 PropagationContext；Snapshot.toState() 只还原快照内容。

## 本地执行

```java
final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
final ContextManager contexts = new ContextManager(backend, backend);
final ContextState state = ContextState.of(invocationContext, authContext);
final ContextProxy proxy = contexts.with(state);

proxy.run(() -> service.handle());
final Result result = proxy.call(() -> service.query());
```

创建 Proxy 不改变当前线程状态。Proxy 持有创建时确定的完整 State，可重复使用；每次 run/call 都建立独立作用域，正常或异常退出均恢复外层状态。业务读取由 ContextReader 或 accessor 完成。

显式派生当前状态：`contexts.with(contexts.state().with(otherAuth)).run(task)`。Proxy 不提供另一套可变数据 API；`proxy.state()` 读取准备执行的数据，`contexts.state()` 读取当前生效的数据。

## 异步传播

```java
final ContextSnapshot snapshot = contexts.capture();
final ContextProxy proxy = contexts.from(snapshot);
executor.execute(proxy.wrap(task));
```

capture 固定传播内容，from 创建仅含快照数据的代理。执行时不合并目标线程已有数据，包括普通本地上下文和旧身份；退出后恢复目标线程原状态。空快照建立空作用域。

`contexts.with(fullState).wrap(task)` 则显式携带整个 State，适合调用方明确指定完整执行环境的情况。需要传播过滤时必须先 capture/from。

## 协议传播

出站：`当前 State → Snapshot → 协议字段`。

```java
propagator.inject(contexts.capture(), headers::set);
```

入站：`协议字段 → Snapshot → Proxy → handler`。

```java
final ContextSnapshot snapshot = propagator.extract(headers::get);
contexts.from(snapshot).run(handler);
```

ContextPropagator 不读取 Store、不绑定线程、不执行 handler。协议适配器负责组合流程并确定来源信任策略，Codec 负责字段校验与转换。入站需补充本地信息时，显式使用 `snapshot.toState().with(localContext)` 再创建 Proxy。

## 底层绑定与 ScopedValue

ThreadLocalContextBackend 直接持有 ThreadLocal，在回调前绑定状态，在 finally 中恢复。状态替换是后端的私有方法，不再设置独立 Store 或 ContextWriter 接口。

对外接入契约只有 ContextReader 与 ContextBinder。默认 ThreadLocalContextBackend 同时实现两者，确保读取与绑定使用同一份存储；ContextManager 和业务组件仍按接口依赖。Spring 默认注册一个后端 Bean。自定义后端必须同时提供匹配的 Reader 和 Binder；只覆盖其中一个接口不是完整替换。

ScopedValueContextBackend 使用相同的 ContextReader、ContextBinder 接口，直接持有 ScopedValue，依赖 JDK 管理绑定与恢复。配套的 [JDK 21 ScopedValue 示例](../wuli3-context-propagation/examples/scoped-value/README.md) 使用 where(...).run/call，主模块不启用预览特性。

StructuredTaskScope 会继承 ScopedValue 全部绑定。需要过滤本地 Context 时，应先 capture/from，并在过滤后的作用域内创建结构化任务作用域；不能把自动继承视为快照过滤。

作用域只覆盖同步回调，不延长到回调返回的 Future 完成时。线程池与普通虚拟线程使用显式包装传播。
