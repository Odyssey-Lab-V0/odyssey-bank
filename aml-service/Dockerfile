FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY aml-service/pom.xml aml-service/

RUN mvn dependency:go-offline -pl banking-shared,aml-service -am -q

COPY banking-shared/src banking-shared/src
COPY aml-service/src aml-service/src
RUN mvn package -pl aml-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S banking && adduser -S banking -G banking
USER banking

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

COPY --from=builder /build/aml-service/target/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
