FROM openjdk:11-jdk-slim
ADD target/discovery-*.jar /discovery.jar
ENV CONTAINER_NAME=localhost \
    PORT=8761 \
    OPTS_ARGS=''

ENTRYPOINT ["java", "-jar", "/discovery.jar", "${OPTS_ARGS}"]
