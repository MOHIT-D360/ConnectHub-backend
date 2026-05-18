FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl presence-service -am package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=build /workspace/presence-service/target/presence-service-1.0.0.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]
