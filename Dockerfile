FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle/
RUN ./gradlew dependencies --no-daemon -q

COPY src src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

RUN groupadd -r app && useradd -r -g app -u 1001 app

COPY --from=build --chown=app:app /workspace/build/libs/*.jar ./app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
