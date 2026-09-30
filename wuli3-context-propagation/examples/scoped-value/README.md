# ScopedValue 绑定实现示例

`ScopedValueContextBackend.java` 基于 **JDK 21 预览 API**，同时实现 `ContextReader` 和 `ContextBinder`。它只在回调执行期间绑定完整 `ContextState`，不提供任意替换当前状态的 `write` 操作，也不依赖 ThreadLocal 的底层存储。

示例放在 `examples/`，不属于 Gradle 的生产源码集，不会使生产构建开启预览功能。预览 API 可能随 JDK 版本变化，迁移时应针对目标 JDK 重新编译和检查 API。

## 装配与使用

```java
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.example.ScopedValueContextBackend;

final ScopedValueContextBackend backend = new ScopedValueContextBackend();
final ContextManager contexts = new ContextManager(backend, backend);

// 替换为业务实际需要的一组上下文；空状态也会遮蔽外层绑定。
final ContextState state = ContextState.empty();
contexts.with(state).run(() -> {
    final ContextState current = contexts.state();
    System.out.println(current.isEmpty());
});
```

读取与绑定必须引用同一个 `backend`，因为每个实例持有独立的 ScopedValue 键。嵌套调用会遮蔽外层状态，正常返回或抛出异常后由 JDK 恢复外层绑定。`call` 直接委托 JDK 的 `Carrier.call(Callable)`，保留任务的返回值和异常。

## 跨线程边界

普通执行器不自动传播绑定。提交前捕获传播快照，再包装任务：

```java
final Runnable wrapped = contexts.from(contexts.capture()).wrap(task);
executor.execute(wrapped);
```

`capture()` 只保留 `PropagationContext`，`wrap` 固定这份快照对应的状态，不会在执行时重新捕获或合并目标线程的上下文。直接使用 `contexts.with(contexts.state()).wrap(task)` 则显式传播完整状态，包括仅限本地使用的上下文。

JDK 21 的 `StructuredTaskScope` 会在**创建作用域时**捕获 ScopedValue 绑定，并让 `fork` 创建的子线程继承**完整状态**；它不会调用框架的 `capture()`。如果子任务只允许获得可传播上下文，必须先进入筛选后的绑定，再创建并关闭结构化任务作用域：

```java
contexts.from(contexts.capture()).call(() -> {
    try (final var scope = new java.util.concurrent.StructuredTaskScope<String>()) {
        final var result = scope.fork(task);
        scope.join();
        return result.get();
    }
});
```

这里 `task` 的类型为 `Callable<String>`。作用域的创建、`fork`、`join` 和关闭均在同一个绑定回调内完成，不能跨越绑定生命周期；任务失败时也需要按具体应用的结构化并发策略处理结果。

## 独立编译

在仓库根目录使用 JDK 21 执行以下命令，只编译，不运行测试：

```sh
./gradlew :wuli3-context-propagation:compileJava
mkdir -p wuli3-context-propagation/build/examples/scoped-value
javac --enable-preview --release 21 \
  -cp wuli3-context-propagation/build/classes/java/main:wuli3-core/build/classes/java/main \
  -d wuli3-context-propagation/build/examples/scoped-value \
  wuli3-context-propagation/examples/scoped-value/ScopedValueContextBackend.java
```

应用引用该示例时也需要用 JDK 21 的 `--enable-preview --release 21` 编译，并以 `java --enable-preview` 启动；将上述输出目录及应用实际依赖加入 classpath。该示例自身未直接使用 JSpecify，无需为此额外添加依赖。

API 依据：[JDK 21 ScopedValue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ScopedValue.html) 与 [ScopedValue.Carrier](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ScopedValue.Carrier.html)。
