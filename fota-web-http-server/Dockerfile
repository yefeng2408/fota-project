FROM eclipse-temurin:17-jre

WORKDIR /app

COPY fota-web-http-server/target/fota-web-http-server-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]