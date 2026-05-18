FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY message-service/target/message-service-1.0.0.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
