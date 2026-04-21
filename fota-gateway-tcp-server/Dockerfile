FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY fota-common/pom.xml fota-common/pom.xml
COPY fota-web-http-server/pom.xml fota-web-http-server/pom.xml
COPY fota-gateway-tcp-server/pom.xml fota-gateway-tcp-server/pom.xml
COPY fota-mock-device-client/pom.xml fota-mock-device-client/pom.xml

RUN mvn -B -N dependency:go-offline || true

COPY . .

RUN mvn -pl fota-gateway-tcp-server -am clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=builder /app/fota-gateway-tcp-server/target/*.jar app.jar

EXPOSE 8224 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]