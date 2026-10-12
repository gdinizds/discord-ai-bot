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

# Flags de Alta Performance e Otimizações Oracle GraalVM (GFTC) JIT em Container:
# - Gerenciamento de Memória: UseContainerSupport com Max/InitialRAMPercentage (reserva 75% da RAM do container para heap, mantendo 25% para off-heap, Metaspace, compilação JIT e OS)
# - Coletor de Lixo: ZGC geracional (-XX:+UseZGC, nativo no JDK 25) para pausas sub-milissegundo (<1ms) e alto throughput
# - Cabeçalhos Compactos: UseCompactObjectHeaders reduz em até 20% o footprint de objetos e árvores de contexto/DTOs em memória
# - Otimização de Heap: AlwaysPreTouch evita stalls de page-fault em tempo de execução
# - Otimização de Memória: UseStringDeduplication reduz pegada de memória de strings duplicadas
# - Compilador Oracle GraalVM Enterprise: CompilerConfiguration=enterprise + Vectorization (SIMD) + PriorityInlining para taxa máxima de throughput
# - Compilação em Camadas: TieredCompilation (C1 para inicialização rápida + GraalVM JIT no Tier 4 para pico)
# - Resiliência e DNS: ExitOnOutOfMemoryError para fail-fast limpo + TTL de 30s no DNS para service discovery dinâmico no Kubernetes
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+AlwaysPreTouch \
    -XX:+UseZGC \
    -XX:+UseCompactObjectHeaders \
    -XX:+UseStringDeduplication \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+EnableJVMCI \
    -XX:+UseJVMCICompiler \
    -Djdk.graal.CompilerConfiguration=enterprise \
    -Djdk.graal.Vectorization=true \
    -Djdk.graal.UsePriorityInlining=true \
    -XX:+TieredCompilation \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    -Dnetworkaddress.cache.ttl=30 \
    -Dnetworkaddress.cache.negative.ttl=10 \
    -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar \"$@\""]
