FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY media-service/target/media-service-1.0.0.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
