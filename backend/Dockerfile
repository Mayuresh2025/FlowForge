
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .

RUN mvn -q dependency:go-offline

COPY src ./src

# Run regular tests. The HTTP integration tests use the *IT.java
# naming convention and are executed separately against a live backend.
RUN mvn -q package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 flowforge

COPY --from=build /workspace/target/flowforge-api-1.0.0.jar app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
