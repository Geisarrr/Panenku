FROM gradle:8.14.3-jdk17 AS build
WORKDIR /src
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle bootJar --no-daemon --max-workers=2

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/private && chmod 700 /app/private && chown 10001:10001 /app/private
COPY --from=build /src/build/libs/*.jar /app/server.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
