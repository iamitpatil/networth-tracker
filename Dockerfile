# ── Stage 1: Build frontend ──
FROM node:22-alpine AS frontend-build
WORKDIR /app/web
COPY web/package.json web/package-lock.json* ./
RUN npm ci --prefer-offline
COPY web/ .
RUN npm run build

# ── Stage 2: Build backend ──
FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 3: Production image (backend only, frontend served by nginx) ──
FROM eclipse-temurin:21-jre-alpine AS backend
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/uploads /app/logs \
    && chown -R appuser:appgroup /app

COPY --from=backend-build --chown=appuser:appgroup /app/target/networth-tracker-*.jar app.jar

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75", \
    "-jar", "app.jar"]

# ── Stage 4: Nginx frontend (serves static files + proxies API) ──
FROM nginx:alpine AS frontend
COPY --from=frontend-build /app/web/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
