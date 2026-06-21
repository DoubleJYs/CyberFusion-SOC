FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace/backend

COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-jammy

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=dev \
    APP_UPLOAD_BASE_DIR=/data/uploads \
    LOGGING_FILE_PATH=/app/logs

WORKDIR /app

RUN mkdir -p /data/uploads /app/logs

COPY --from=build /workspace/backend/target/sec-wazuh-soc-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/health/liveness" >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
