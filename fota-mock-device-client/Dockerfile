FROM eclipse-temurin:17-jre

WORKDIR /app

COPY fota-mock-device-client/target/fota-mock-device-client-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]