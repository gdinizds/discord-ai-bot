FROM container-registry.oracle.com/graalvm/native-image:25 AS build
WORKDIR /workspace
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Keep the Oracle Linux native-image toolchain's OS packages current at build time.
RUN microdnf -y upgrade && microdnf clean all

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle/
COPY src src/
RUN chmod +x gradlew \
    && ./gradlew nativeCompile --no-daemon -x test \
    && rm -rf /root/.gradle

# Provides glibc, libstdc++, zlib and CA certificates without a shell or package manager.
FROM gcr.io/distroless/cc-debian13:nonroot AS runtime
WORKDIR /app
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

COPY --from=build --chown=1001:1001 /workspace/build/native/nativeCompile/discord-ai-bot /app/discord-ai-bot

USER 1001:1001

EXPOSE 8080

ENTRYPOINT ["/app/discord-ai-bot", "-XX:MaximumHeapSizePercent=50"]
