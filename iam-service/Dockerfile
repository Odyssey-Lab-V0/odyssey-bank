FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy root pom + ALL module poms so Maven can parse the full reactor
COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY iam-service/pom.xml iam-service/
COPY banking-core-service/pom.xml banking-core-service/
COPY onboarding-service/pom.xml onboarding-service/
COPY kyc-service/pom.xml kyc-service/
COPY aml-service/pom.xml aml-service/
COPY notification-service/pom.xml notification-service/

# Download dependencies (cached layer — only invalidated when poms change)
RUN mvn dependency:go-offline -pl banking-shared,iam-service -am -q

# Copy only the source we need and build
COPY banking-shared/src banking-shared/src
COPY iam-service/src iam-service/src
RUN mvn package -pl iam-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S banking && adduser -S banking -G banking
USER banking

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

COPY --from=builder /build/iam-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
