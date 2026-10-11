FROM container-registry.oracle.com/graalvm/jdk:25 AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src src/
RUN ./gradlew bootJar --no-daemon -x test

FROM container-registry.oracle.com/graalvm/jdk:25 AS runtime
WORKDIR /app

# Cria usuário não-root no Oracle Linux Slim
RUN microdnf install -y shadow-utils \
    && groupadd -r app \
    && useradd -r -g app -u 1001 app \
    && microdnf clean all \
    && rm -rf /var/cache/yum

COPY --from=build --chown=app:app /workspace/build/libs/*.jar ./app.jar

USER app

EXPOSE 8080

# Flags de Performance e GC para GraalVM em Container:
# - Gerenciamento de Memória: UseContainerSupport com Max/InitialRAMPercentage (reserva 75% da RAM do container para heap, mantendo 25% para off-heap, Metaspace, compilação JIT e OS)
# - Coletor de Lixo: Generational ZGC (-XX:+UseZGC -XX:+ZGenerational) para pausas sub-milissegundo (<1ms) e alto throughput
# - Otimização de Heap: AlwaysPreTouch evita stalls de page-fault em tempo de execução
# - Otimização de Memória: UseStringDeduplication reduz pegada de memória de strings duplicadas
# - Compilador GraalVM JIT: EnableJVMCI + UseJVMCICompiler ativa as otimizações do Graal JIT compiler
# - Resiliência: ExitOnOutOfMemoryError força fail-fast e reinício saudável do container em caso de OOM
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=75.0 \
    -XX:+AlwaysPreTouch \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:+UseStringDeduplication \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+EnableJVMCI \
    -XX:+UseJVMCICompiler \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar \"$@\""]
