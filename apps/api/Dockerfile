FROM clojure:temurin-25-tools-deps-trixie AS build

WORKDIR /workspace/apps/api

COPY apps/api/deps.edn /workspace/apps/api/deps.edn
COPY packages/shared/deps.edn /workspace/packages/shared/deps.edn
RUN mkdir -p /workspace/packages/shared/src && clojure -A:build -P

COPY packages /workspace/packages
COPY apps/api /workspace/apps/api

RUN clojure -T:build uber

FROM eclipse-temurin:25-jre-noble AS runtime

RUN useradd --system --create-home --uid 10001 app \
  && apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build --chown=app:app /workspace/apps/api/target/api-0.1.0-standalone.jar /app/api.jar

USER app
EXPOSE 8080

ENV API_HOST=0.0.0.0 API_PORT=8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS "http://localhost:${API_PORT}/api/v1/health" || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/api.jar"]
