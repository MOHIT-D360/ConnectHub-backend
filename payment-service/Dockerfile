FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY payment-service/target/payment-service-1.0.0.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
