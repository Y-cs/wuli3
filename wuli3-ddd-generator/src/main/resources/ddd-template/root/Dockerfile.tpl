FROM registry.cn-hangzhou.aliyuncs.com/kr36-env/openjdk:21-jdk-slim

ENV BASE_HOME=/opt/www
ENV LANG en_US.UTF-8

WORKDIR $BASE_HOME

COPY bootstrap/build/libs/bootstrap.jar app.jar

EXPOSE 8080 8099

ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -jar app.jar ${OPTIONS}"]
