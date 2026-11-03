# syntax=docker/dockerfile:1

FROM node:22-alpine AS web
WORKDIR /build/web
RUN npm install -g pnpm@11.5.2
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY core/pom.xml core/pom.xml
COPY analyzers/java/pom.xml analyzers/java/pom.xml
COPY worker/pom.xml worker/pom.xml
COPY cli/pom.xml cli/pom.xml
COPY server/pom.xml server/pom.xml
COPY core core
COPY analyzers analyzers
COPY worker worker
COPY cli cli
COPY server server
COPY --from=web /build/web/dist web/dist
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache git \
    && addgroup -S -g 10001 moheng \
    && adduser -S -u 10001 -G moheng -h /app moheng

WORKDIR /app
COPY --from=build /build/server/target/moheng-server.jar /app/moheng-server.jar
COPY --from=build /build/worker/target/moheng-worker.jar /app/lib/moheng-worker.jar
COPY demo /app/demo-src
COPY deploy/entrypoint.sh /app/entrypoint.sh
COPY LICENSE NOTICE /app/licenses/

RUN sh /app/demo-src/setup.sh /workspace/demo \
    && mkdir -p /data \
    && chown -R 10001:10001 /app /workspace /data \
    && chmod +x /app/entrypoint.sh

ENV MOHENG_BIND_ADDRESS=0.0.0.0 \
    MOHENG_PORT=8848 \
    MOHENG_WORKSPACE_ROOT=/workspace \
    MOHENG_DATA_DIR=/data \
    MOHENG_WORKER_JAR=/app/lib/moheng-worker.jar \
    MOHENG_WORKER_HEAP=1g \
    MOHENG_ANALYSIS_TIMEOUT_SECONDS=600 \
    MOHENG_DEMO_REPO=/workspace/demo

USER 10001:10001
EXPOSE 8848

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8848/api/health >/dev/null || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
