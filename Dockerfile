# Multi-stage build for Spring Boot app
FROM maven:3.9-eclipse-temurin-21 AS build
COPY . /app
WORKDIR /app/api
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
