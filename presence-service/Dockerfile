FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY presence-service/target/presence-service-1.0.0.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]
