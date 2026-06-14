FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY kyc-service/pom.xml kyc-service/

RUN mvn dependency:go-offline -pl banking-shared,kyc-service -am -q

COPY banking-shared/src banking-shared/src
COPY kyc-service/src kyc-service/src
RUN mvn package -pl kyc-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S banking && adduser -S banking -G banking
USER banking

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

COPY --from=builder /build/kyc-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
