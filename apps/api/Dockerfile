FROM clojure:temurin-21-tools-deps-bookworm AS build

WORKDIR /workspace/apps/api

COPY apps/api/deps.edn /workspace/apps/api/deps.edn
COPY packages/shared/deps.edn /workspace/packages/shared/deps.edn
RUN mkdir -p /workspace/packages/shared/src && clojure -P

COPY packages /workspace/packages
COPY apps/api /workspace/apps/api

RUN clojure -M -e "(compile 'app.api.main)" \
  && clojure -Spath > /workspace/classpath.txt

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN useradd --system --create-home --uid 10001 app
WORKDIR /app

COPY --from=build /root/.m2 /root/.m2
COPY --from=build /workspace /workspace
COPY --from=build /workspace/classpath.txt /app/classpath.txt

USER app
EXPOSE 8080

ENV API_HOST=0.0.0.0 API_PORT=8080

CMD ["sh", "-c", "java -cp $(cat /app/classpath.txt) clojure.main -m app.api.main"]
