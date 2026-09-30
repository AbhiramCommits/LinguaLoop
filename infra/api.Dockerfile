# LinguaLoop API — multi-stage build
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S lingualoop && adduser -S lingualoop -G lingualoop
COPY --from=build /workspace/build/libs/lingualoop-api-0.1.0.jar app.jar
USER lingualoop
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
