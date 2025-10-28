# Build stage - includes Actuator & Prometheus metrics
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
WORKDIR /app/service
RUN mvn -pl task-queue -am clean package -DskipTests

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/service/task-queue/target/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
