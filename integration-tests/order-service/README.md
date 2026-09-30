# order-service-integration-tests

本目录是 Wuli3 的独立订单服务集成测试夹具，专门验证发布后的 Starter 与真实基础设施组合行为。它与 `examples/order-service` 完全独立：后者只是 DDD Generator 生成示例，本工程不引用或修改它。

## 工程结构

模块使用 `order-it-` 前缀，包名使用 `com.kjs.wuli3.it.order`：

- `order-it-shared-kernel`、`order-it-domain`、`order-it-api`、`order-it-app`：订单领域和应用用例；
- `order-it-infra`、`order-it-adapter`、`order-it-bootstrap`：MySQL、Redis、RocketMQ v5、HTTP 和启动组装；
- `order-it-acceptance`：真实基础设施验收与架构测试。

## 运行

需要 JDK 21、Docker Compose 和可用的 Docker daemon。先发布当前工作区产物，再启动专用容器并运行验收：

```bash
./gradlew verifyOrderServiceIntegration
```

也可从本目录运行 `./run.sh verify`。只运行单元测试、架构测试和质量检查时，在仓库根目录执行：

```bash
./gradlew publishAllPublicationsToTemporaryRepository
./gradlew -p integration-tests/order-service check
```

`verify` 使用随机的 `order-it-*` Compose 工程名，并在结束时只清理本次创建的容器、网络和卷；容器日志保存在 `build/logs/`。也可以进入本目录分步运行：

```bash
COMPOSE_PROJECT_NAME=order-it-local ./run.sh start
./run.sh test
COMPOSE_PROJECT_NAME=order-it-local ./run.sh stop
```

默认端口为 MySQL `3307`、Redis `6380`、RocketMQ v5 gRPC Proxy `8081`。可通过 `ORDER_IT_MYSQL_PORT`、`ORDER_IT_REDIS_PORT`、`ORDER_IT_MQ_ENDPOINT`、`ORDER_IT_MQ_TOPIC` 等环境变量覆盖。普通 `check` 只运行单元和静态检查；真实容器验收必须显式运行 `integrationTest` 或 `run.sh verify`。

## 验收边界

测试覆盖 HTTP 创建/查询和错误响应、MySQL 持久化与唯一约束、Redis 缓存 TTL 与分布式锁、RocketMQ v5 事件，以及事务提交后发送、回滚不发送。消息使用同步发送以便从真实 Consumer 确认送达。

验收不承诺 Outbox、消费幂等、死信、持久重试或提交后进程崩溃恢复。`afterCommit` 是事务同步回调，发送失败发生在数据库提交之后时不会回滚已提交数据。

## 配置与隔离

MySQL 支持 `ORDER_IT_MYSQL_URL` 或 `ORDER_IT_MYSQL_HOST/PORT/DATABASE`，以及
`ORDER_IT_MYSQL_USER/PASSWORD`；初始化建表可用 `ORDER_IT_SQL_INIT_MODE=never` 关闭。
Redis 使用 `ORDER_IT_REDIS_HOST/PORT`，外部受保护实例可使用 Spring 标准变量 `SPRING_DATA_REDIS_PASSWORD`。
RocketMQ 使用 `ORDER_IT_MQ_ENDPOINT`、`ORDER_IT_MQ_TOPIC` 和 `ORDER_IT_MQ_GROUP`，本地明文连接默认禁用 SSL。
Compose 映射端口可用 `ORDER_IT_MQ_PORT` 调整，脚本会据此生成默认 endpoint。
`ORDER_IT_RESOURCE_PREFIX` 控制 Redis 键前缀，测试订单使用独立 UUID，不执行清库或通配删除。

Compose 专用于本地验收，不要配置生产服务地址。使用外部服务时，必须预先创建普通消息 topic 和独占 consumer group；
验收消费者会确认读取到的消息，不与业务消费者共享 group。
固定镜像为 MySQL 8.4.6、Redis 7.4.5、RocketMQ 5.3.2；Java v5 客户端版本由 Wuli3 BOM 管理。
接口为 `POST /api/orders`（`{"id":"order-1"}`）和 `GET /api/orders/{id}`，订单仅有 CREATED 状态。
创建入口自行管理事务时锁覆盖提交；参与外层事务时仍由数据库唯一约束兜底，不承诺跨外层事务持锁。

JUnit 报告及应用日志位于 `order-it-acceptance/build/reports/tests/{test,integrationTest}/`。
端口占用时可覆盖上述端口；Docker 不可用时脚本明确失败，不静默跳过验收。
