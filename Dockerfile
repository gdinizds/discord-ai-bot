FROM container-registry.oracle.com/graalvm/native-image:25 AS build
WORKDIR /workspace
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src src/
RUN ./gradlew nativeCompile --no-daemon -x test

FROM debian:bookworm-slim AS runtime
WORKDIR /app
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
        libstdc++6 \
        zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app \
    && useradd -r -g app -u 1001 app

COPY --from=build --chown=app:app /workspace/build/native/nativeCompile/discord-ai-bot /app/discord-ai-bot
RUN chmod +x /app/discord-ai-bot

USER app

EXPOSE 8080

ENTRYPOINT ["/app/discord-ai-bot", "-XX:MaximumHeapSizePercent=50"]


