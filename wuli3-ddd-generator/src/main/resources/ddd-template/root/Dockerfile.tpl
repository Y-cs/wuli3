ARG TARGET_ARCH=linux/amd64

FROM --platform=$TARGET_ARCH registry.cn-hangzhou.aliyuncs.com/kr36-env/openjdk:21-jdk-slim-otel

ENV BASE_HOME=/opt/www
ENV LANG=en_US.UTF-8

#############################################
#
# OpenTelemetry Java Agent
#
# 接入 collector 时，部署环境必须同时设置 OTEL_JAVAAGENT_ENABLED=true 和 OTEL_EXPORTER_OTLP_ENDPOINT。
#
# 以下变量不固化在镜像中，由部署环境按需注入，未设置时使用 agent 内置默认值：
#   OTEL_EXPORTER_OTLP_ENDPOINT   取值：URL。内置默认 http://localhost:4318（http）或 http://localhost:4317（grpc）
#   OTEL_EXPORTER_OTLP_HEADERS    取值：k1=v1,k2=v2。鉴权请求头，含凭据，禁止写入镜像
#   OTEL_TRACES_SAMPLER           取值：always_on | always_off | traceidratio | parentbased_always_on |
#                                 parentbased_always_off | parentbased_traceidratio | jaeger_remote |
#                                 parentbased_jaeger_remote | xray。默认 parentbased_always_on，即全量采样
#   OTEL_TRACES_SAMPLER_ARG       取值：0.0 至 1.0 的采样率，配合 traceidratio 系列采样器使用
#   OTEL_TRACES_EXPORTER          取值：otlp | zipkin | console | logging | none。默认 otlp
#   OTEL_METRICS_EXPORTER         取值：otlp | prometheus | console | logging | none。默认 otlp，按 60s 定时导出
#   OTEL_LOGS_EXPORTER            取值：otlp | console | logging | none。默认 otlp
#   OTEL_RESOURCE_ATTRIBUTES      取值：k1=v1,k2=v2，例如 deployment.environment=prod
#   OTEL_JAVAAGENT_DEBUG          取值：true | false。agent 自身 debug 日志开关
#   OTEL_JAVAAGENT_LOGGING        取值：simple | none | application。agent 日志输出方式，默认 simple
#
#############################################

# 服务名；取值：任意非空字符串
ENV OTEL_SERVICE_NAME={{service}}
# agent 总开关；取值：true | false
ENV OTEL_JAVAAGENT_ENABLED=false
# 导出协议；取值：grpc | http/protobuf | http/json。http/protobuf 走 4318 端口，grpc 走 4317
ENV OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf


#  workdir
#------------------------------------------
WORKDIR $BASE_HOME

# JUL FileHandler 和 Spring Boot 文件日志都要求日志目录在启动前存在。
RUN mkdir -p logs

COPY bootstrap/build/libs/bootstrap.jar app.jar

EXPOSE 8080 8099

ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -javaagent:/opt/otel/opentelemetry-javaagent.jar -jar app.jar ${OPTIONS}"]
