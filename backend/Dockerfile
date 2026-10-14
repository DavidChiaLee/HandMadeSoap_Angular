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

# 健康檢查（image 自帶預設，liveness 語意）。打 /health 而非 /readiness：
# readiness 含 DataSource 檢查，屬「編排層」關切的事，由 docker-compose.yml 的
# healthcheck 負責（它會覆蓋這裡，且 waits-for DB）。這裡只作為 image 獨立運行時
# 的預設存活探針。與 compose 使用同一個 wget（上方已 apk 安裝）。
# start-period 給 Spring Boot 啟動 + Flyway 遷移緩衝，避免剛起就被判不健康。
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

# -XX:MaxRAMPercentage=75：讓 JVM 依容器 cgroup 的記憶體上限自動取 75% 作為最大堆，
# 而非預設的 1/4（預設值在小容器上浪費記憶體、在大容器上堆過大易被 OOM-kill）。
# 不寫死 -Xmx：寫死就無法隨 compose / K8s 調整 container 記憶體自動縮放。
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
