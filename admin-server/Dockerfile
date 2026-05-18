FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl admin-server -am package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=build /workspace/admin-server/target/admin-server-1.0.0.jar app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar"]
