# 5개 서비스가 공유하는 런타임 전용 Dockerfile. 서비스별로 따로
# Dockerfile을 두지 않고, 미리 빌드된 bootJar를 JAR_PATH로 받아 그대로
# 실행 이미지에 올린다 — 각 서비스 이미지를 빌드하기 전에 호스트에서
# ./gradlew bootJar (전체) 또는 :<service>:bootJar를 먼저 실행해야 한다.
FROM eclipse-temurin:21-jre

ARG JAR_PATH
WORKDIR /app
COPY ${JAR_PATH} app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
