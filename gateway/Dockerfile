FROM openjdk:11-jdk-slim
ADD target/gateway-*.jar /gateway.jar
ENV CONTAINER_NAME=localhost \
    PORT=8763 \
    OPTS_ARGS=''

ENTRYPOINT ["java", "-jar", "/gateway.jar", "${OPTS_ARGS}"]
