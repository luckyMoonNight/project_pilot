# ============================================================
# 阶段 1：构建（builder）—— 用带 Maven + JDK 21 的镜像编译项目
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先单独拷贝 pom.xml 并下载依赖。利用 Docker 层缓存：
# 只要 pom.xml 没变，下次构建就跳过重新下载依赖（速度快很多）
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再拷贝源码并打包（跳过测试以加快构建）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ============================================================
# 阶段 2：运行（runner）—— 只用轻量 JRE 镜像，体积小很多
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户运行，安全实践
RUN addgroup -S app && adduser -S app -G app

# 从构建阶段把可执行 jar 拷过来
COPY --from=builder /build/target/*.jar app.jar

# 暴露 Spring Boot 默认端口
EXPOSE 8080

USER app

# 容器内时区设为东八区，日志时间戳更直观
ENV TZ=Asia/Shanghai

# 用 exec 形式启动，确保容器收到 SIGTERM 时 JVM 能正常关闭
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
