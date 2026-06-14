# Multi-stage build — production image is ~80MB, not 500MB
# Stage 1: build the JAR
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy parent POM + shared module POM first (layer cache — deps change rarely)
COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY iam-service/pom.xml iam-service/

# Download dependencies before copying source (cache this layer)
RUN mvn dependency:go-offline -pl banking-shared,iam-service -am -q

# Now copy source and build
COPY banking-shared/src banking-shared/src
COPY iam-service/src iam-service/src
RUN mvn package -pl iam-service -am -DskipTests -q

# Stage 2: slim runtime image
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S banking && adduser -S banking -G banking
USER banking

# JVM tuning for containers: respect cgroup memory limits
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseZGC \
               -Djava.security.egd=file:/dev/./urandom"

COPY --from=builder /build/iam-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
