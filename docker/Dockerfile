# ===== Stage 1: 前端构建（Vue 3 + Vite） =====
FROM node:20-alpine AS frontend-builder

# 使用淘宝镜像加速 npm
RUN npm config set registry https://registry.npmmirror.com

WORKDIR /build
# 先复制 package.json 利用 Docker 层缓存（package-lock.json 可选，缺失时退化为 npm install）
COPY frontend/package.json frontend/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

# 复制前端源码并构建
COPY frontend/ .
RUN npm run build
# 产物：/build/dist/

# ===== Stage 2: 后端构建（Maven，内嵌前端 dist 到 classpath:/static/） =====
FROM maven:3.9-eclipse-temurin-21 AS backend-builder

# 阿里云 Maven 镜像加速
COPY docker/maven-settings.xml /root/.m2/settings.xml

WORKDIR /build

# 先复制 POM 利用层缓存（下载依赖）
COPY backend/pom.xml .
COPY backend/checkstyle.xml .

# 复制源码（注意：app/src/main/resources/static/ 会被下一步注入前端 dist）
COPY backend/platform-common ./platform-common
COPY backend/platform-security ./platform-security
COPY backend/platform-starter ./platform-starter
COPY backend/modules ./modules
COPY backend/app ./app

# 注入前端构建产物到 Spring Boot 静态资源目录
# （application.yml 已配 static-locations: classpath:/static/,file:/app/static/optional/）
COPY --from=frontend-builder /build/dist/ ./app/src/main/resources/static/

# 构建 fat jar（跳过测试/代码质量检查，由 CI 其他环节保障）
RUN mvn package -DskipTests -B \
    -Dspotless.check.skip=true \
    -Dcheckstyle.skip=true \
    -Dspotbugs.skip=true \
    -Djacoco.skip=true

# ===== Stage 3: 运行时（精简 JRE） =====
FROM eclipse-temurin:21-jre

WORKDIR /app

# 创建可选的外挂静态目录（支持不重新打 jar 更新前端：挂载卷覆盖）
RUN mkdir -p /app/static/optional

# 复制 fat jar
COPY --from=backend-builder /build/app/target/*.jar app.jar

# JVM 参数 + Profile
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE=prod

# 暴露端口（HTTP API + SPA + WebSocket 共用）
EXPOSE 8090

# 启动
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
