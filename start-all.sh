#!/bin/bash
# Start all Odyssey Bank services
# Usage: ./start-all.sh

JAVA=/Library/Java/JavaVirtualMachines/jdk-18.0.1.1.jdk/Contents/Home/bin/java
PLATFORM=/Users/ruchikr0905/banking-platform
LOGS=/tmp/odyssey-logs

mkdir -p $LOGS

echo "Starting Zipkin on :9411..."
zipkin > $LOGS/zipkin.log 2>&1 &
echo "Zipkin PID=$!"
sleep 5

echo "Starting IAM Service on :8081..."
$JAVA -jar $PLATFORM/iam-service/target/iam-service-1.0.0-SNAPSHOT.jar > $LOGS/iam.log 2>&1 &
echo "IAM PID=$!"

echo "Starting Banking Core on :8082..."
$JAVA -jar $PLATFORM/banking-core-service/target/banking-core-service-1.0.0-SNAPSHOT.jar > $LOGS/banking-core.log 2>&1 &
echo "Banking Core PID=$!"

echo "Starting Onboarding Service on :8083..."
$JAVA -jar $PLATFORM/onboarding-service/target/onboarding-service-1.0.0-SNAPSHOT.jar > $LOGS/onboarding.log 2>&1 &
echo "Onboarding PID=$!"

echo "Starting KYC Service on :8084..."
$JAVA -jar $PLATFORM/kyc-service/target/kyc-service-1.0.0-SNAPSHOT.jar > $LOGS/kyc.log 2>&1 &
echo "KYC PID=$!"

echo "Starting AML Service on :8085..."
$JAVA -jar $PLATFORM/aml-service/target/aml-service-1.0.0-SNAPSHOT.jar > $LOGS/aml.log 2>&1 &
echo "AML PID=$!"

echo "Starting Notification Service on :8086..."
$JAVA -jar $PLATFORM/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar > $LOGS/notification.log 2>&1 &
echo "Notification PID=$!"

echo ""
echo "All services starting. Logs in $LOGS/"
echo "Zipkin UI → http://localhost:9411"
