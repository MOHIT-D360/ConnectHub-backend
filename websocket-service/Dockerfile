FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl websocket-service -am package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=build /workspace/websocket-service/target/websocket-service-1.0.0.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]
