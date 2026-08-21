# RTC control-plane image. Media stays in LiveKit/SRS/TURN containers.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY server/pom.xml server/pom.xml
RUN mvn -f server/pom.xml -B -DskipTests dependency:go-offline
COPY server server
RUN cp server/src/main/resources/application.example.yml server/src/main/resources/application.yml \
    && cp server/src/main/resources/application-dev.example.yml server/src/main/resources/application-dev.yml
RUN mvn -f server/pom.xml -B -DskipTests package

# Reuse the Maven Temurin image for offline-friendly local Docker validation.
# Production deployments can replace this stage with a smaller JRE base image.
FROM maven:3.9.9-eclipse-temurin-17
WORKDIR /app
COPY --from=build /workspace/server/target/douyin-server-1.0.0.jar /app/douyin-server.jar
EXPOSE 9191
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/douyin-server.jar"]
