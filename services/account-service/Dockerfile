# Meridian Bank — account-service
# Multi-stage build: compile with Maven + JDK, run on a slim JRE.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Cache dependency resolution separately from source changes.
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -q -o -DskipTests package || ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S meridian && adduser -S meridian -G meridian
COPY --from=build /build/target/account-service.jar app.jar
USER meridian

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
