FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY admin-server/target/admin-server-1.0.0.jar app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar"]
