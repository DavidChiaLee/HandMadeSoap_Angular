# Build stage：在 image 內打包，不再依賴本機 backend/target/*.jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
# 健康檢查工具（review M7）：jre-alpine 預設無 curl/wget，compose healthcheck
# 打 /actuator/health/readiness 需要其一。這裡裝 wget，體積比 curl 小，
# 與 compose 的 ["CMD", "wget", "-qO-", ...] 必須是同一個工具。
RUN apk add --no-cache wget && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/uploads /app/logs && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
