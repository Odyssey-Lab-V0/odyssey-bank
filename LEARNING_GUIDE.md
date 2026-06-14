# Docker → Kubernetes → K3d → GitOps
## A Complete Beginner-to-Production Tutorial

> Every concept explained from scratch. Every YAML line annotated.
> Real examples from the Odyssey Bank project throughout.

---

## Table of Contents

1. [Docker — Containers 101](#1-docker--containers-101)
2. [Dockerfile Deep Dive](#2-dockerfile-deep-dive)
3. [Docker Compose — Running Multiple Containers](#3-docker-compose--running-multiple-containers)
4. [Why Kubernetes?](#4-why-kubernetes)
5. [Kubernetes Core Concepts](#5-kubernetes-core-concepts)
6. [Kubernetes YAML — Every Field Explained](#6-kubernetes-yaml--every-field-explained)
7. [Kubernetes Networking](#7-kubernetes-networking)
8. [Kubernetes Health Probes](#8-kubernetes-health-probes)
9. [Kubernetes Storage](#9-kubernetes-storage)
10. [Kubernetes Secrets & ConfigMaps](#10-kubernetes-secrets--configmaps)
11. [K3d — Lightweight Local Kubernetes](#11-k3d--lightweight-local-kubernetes)
12. [GitOps — What, Why, How](#12-gitops--what-why-how)
13. [ArgoCD — GitOps Engine](#13-argocd--gitops-engine)
14. [ArgoCD App-of-Apps Pattern](#14-argocd-app-of-apps-pattern)
15. [CI/CD Pipeline End to End](#15-cicd-pipeline-end-to-end)
16. [Real World Gotchas](#16-real-world-gotchas)

---

## 1. Docker — Containers 101

### The problem before Docker

Before containers, deploying an app meant:
- "Works on my machine" — different OS, different Java version, different config
- Setting up servers manually — install Java, install Maven, configure ports
- One bad app can crash the whole server

### What is a container?

A container is a **box** that packages your app + everything it needs to run:
- The OS files it needs (not the full OS, just the libs)
- The runtime (Java 21, Node 20, Python 3.11...)
- Your app code
- Environment variables

```
┌──────────────────────────────────────┐
│           Your Machine               │
│                                      │
│  ┌───────────┐   ┌───────────┐      │
│  │Container 1│   │Container 2│      │
│  │           │   │           │      │
│  │  Java 21  │   │  Node 20  │      │
│  │  iam-svc  │   │  frontend │      │
│  └───────────┘   └───────────┘      │
│                                      │
│  ┌──────────────────────────────┐   │
│  │    Docker Engine (runtime)   │   │
│  └──────────────────────────────┘   │
│  ┌──────────────────────────────┐   │
│  │    Your OS (macOS / Linux)   │   │
│  └──────────────────────────────┘   │
└──────────────────────────────────────┘
```

Containers share the host OS kernel — they are NOT full virtual machines. That's why they start in milliseconds, not minutes.

### Container vs Virtual Machine

| | Container | Virtual Machine |
|--|-----------|----------------|
| Startup | Milliseconds | Minutes |
| Size | MBs | GBs |
| OS | Shares host kernel | Full OS inside |
| Isolation | Process-level | Full hardware |
| Use case | Apps | Full environments |

### Key Docker commands

```bash
# Pull an image from Docker Hub
docker pull postgres:16-alpine

# Run a container
docker run -d \                        # -d = detached (background)
  --name my-postgres \                 # give it a name
  -p 5432:5432 \                       # host:container port mapping
  -e POSTGRES_PASSWORD=secret \        # environment variable
  postgres:16-alpine

# See running containers
docker ps

# See logs
docker logs my-postgres
docker logs -f my-postgres             # -f = follow (live tail)

# Stop / remove
docker stop my-postgres
docker rm my-postgres

# Run a command inside a running container
docker exec -it my-postgres psql -U postgres

# Build an image from a Dockerfile
docker build -t my-app:1.0 .           # -t = tag (name:version), . = current dir

# List images
docker images

# Remove an image
docker rmi my-app:1.0
```

### What is a Docker image vs a container?

```
Image = Blueprint (read-only snapshot)
Container = Running instance of an image

One image → can run many containers
```

Like a class (image) vs an object (container) in OOP.

---

## 2. Dockerfile Deep Dive

A `Dockerfile` is a recipe that tells Docker how to build your image.

### Simple example first

```dockerfile
FROM ubuntu:22.04          # Start from Ubuntu base image

RUN apt-get update         # Run a command during build
RUN apt-get install -y curl

COPY app.sh /app/app.sh    # Copy file from your machine into the image

EXPOSE 8080                # Document which port the app uses

CMD ["bash", "/app/app.sh"]  # What to run when container starts
```

### Multi-stage build (what we use in production)

The problem with simple builds: you need the full JDK (500MB+) to compile Java, but only need the JRE (150MB) to run it. Multi-stage solves this.

```dockerfile
# ═══════════════════════════════════════════════════════
# STAGE 1: Builder
# This stage compiles the code. It will NOT be in the final image.
# ═══════════════════════════════════════════════════════
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
#    │                              │          │
#    │                              │          └── Name this stage "builder"
#    │                              └── Java 21 on Alpine Linux (small)
#    └── Maven pre-installed (for compiling Java)

WORKDIR /build
# Sets the working directory inside the container.
# All subsequent COPY/RUN commands happen relative to /build.

# ── Copy pom files FIRST (before source code) ──────────
# WHY? Docker builds in layers. Each instruction = one layer.
# If source code changes but pom.xml doesn't, Docker reuses
# the cached dependency download layer. Saves 2-3 minutes.
COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY iam-service/pom.xml iam-service/
COPY banking-core-service/pom.xml banking-core-service/
# ... all module pom files

# Download all dependencies (cached layer)
RUN mvn dependency:go-offline -pl banking-shared,iam-service -am -q
#   │                          │                               │
#   │                          │                               └── -q = quiet
#   │                          └── -pl = only these modules, -am = include deps
#   └── Maven goal: download all deps to local repo

# Now copy source (this layer invalidates when source changes)
COPY banking-shared/src banking-shared/src
COPY iam-service/src iam-service/src

# Build the JAR
RUN mvn package -pl iam-service -am -DskipTests -q
#                                     └── skip tests (tests run in CI separately)

# ═══════════════════════════════════════════════════════
# STAGE 2: Runtime
# ONLY this stage ends up in the final image.
# The builder stage is discarded after the COPY below.
# ═══════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine AS runtime
#    │                    │
#    │                    └── JRE only (not full JDK) — 150MB not 500MB
#    └── Official Eclipse Temurin Java distribution

WORKDIR /app

# Security best practice: never run as root inside a container
RUN addgroup -S banking && adduser -S banking -G banking
#                 └── -S = system group/user (no login shell, no home dir)
USER banking
# All commands after this line run as the "banking" user

# JVM flags for container environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseZGC \
               -Djava.security.egd=file:/dev/./urandom"
# │                    │                 │
# │                    │                 └── ZGC: low-latency garbage collector
# │                    └── JVM heap = 75% of container memory limit
# └── Read cgroup memory limits (not host RAM)

# Copy ONLY the JAR from the builder stage
COPY --from=builder /build/iam-service/target/*.jar app.jar
#    │                                               └── destination in runtime image
#    └── "from=builder" = grab from the builder stage above

EXPOSE 8081
# Documents the port. Doesn't actually open it — that's done with -p at runtime.

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
# What runs when the container starts.
# sh -c lets us expand the $JAVA_OPTS variable.
```

### ENTRYPOINT vs CMD

```dockerfile
# ENTRYPOINT = the executable, always runs
ENTRYPOINT ["java", "-jar", "app.jar"]

# CMD = default arguments, can be overridden
CMD ["--spring.profiles.active=prod"]

# Together: runs "java -jar app.jar --spring.profiles.active=prod"
# Override at runtime: docker run myimage --spring.profiles.active=dev
```

### Layer caching explained visually

```
Build #1 (first time):
Layer 1: FROM maven...          [DOWNLOAD ~500MB] ←takes time
Layer 2: WORKDIR /build         [fast]
Layer 3: COPY pom.xml           [fast]
Layer 4: RUN mvn dep:go-offline [DOWNLOAD deps ~200MB] ←takes time
Layer 5: COPY src/              [fast]
Layer 6: RUN mvn package        [COMPILE ~30s]

Build #2 (only source changed):
Layer 1: FROM maven...          [CACHED ✓]
Layer 2: WORKDIR /build         [CACHED ✓]
Layer 3: COPY pom.xml           [CACHED ✓] ← pom didn't change
Layer 4: RUN mvn dep:go-offline [CACHED ✓] ← deps didn't change
Layer 5: COPY src/              [CHANGED — cache miss]
Layer 6: RUN mvn package        [RE-RUN]

Result: Build #2 takes 30s instead of 10 minutes.
```

---

## 3. Docker Compose — Running Multiple Containers

### Why Docker Compose?

Running one container with `docker run` is fine. But a real app needs:
- A database
- A cache (Redis)
- A message broker (Kafka)
- Your actual app

Running 5+ `docker run` commands with all the right flags is painful. Docker Compose defines all of them in one `docker-compose.yml` file.

### Docker Compose structure

```yaml
version: '3.9'           # Compose file format version

services:                # Each entry = one container
  postgres:              # This is the service name (used as DNS hostname too)
    image: postgres:16-alpine   # Which image to use
    container_name: banking-postgres  # Name of the container
    ports:
      - "5432:5432"      # "host_port:container_port"
                         # Access from your Mac at localhost:5432
    environment:         # Environment variables inside the container
      POSTGRES_DB: banking
      POSTGRES_USER: banking
      POSTGRES_PASSWORD: banking
    volumes:             # Mount host path or named volume into container
      - postgres_data:/var/lib/postgresql/data  # named volume (persists data)
      - ./scripts/init.sql:/docker-entrypoint-initdb.d/init.sql:ro  # file mount
    healthcheck:         # How to know if the service is ready
      test: ["CMD-SHELL", "pg_isready -U banking"]
      interval: 5s       # Check every 5 seconds
      timeout: 5s        # Fail if no response in 5s
      retries: 10        # Give up after 10 failures

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb   # Override the default CMD

  kafka:
    image: confluentinc/cp-kafka:7.6.1
    depends_on:
      zookeeper:
        condition: service_healthy   # Wait until zookeeper passes healthcheck
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      # Services talk to each other using service names as hostnames
      # kafka can reach zookeeper at "zookeeper:2181"

  iam-service:
    build:               # Build from Dockerfile instead of pulling image
      context: .         # Build context = current directory
      dockerfile: iam-service/Dockerfile
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    ports:
      - "8082:8081"      # Expose on host port 8082, container port 8081
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/banking
      #                              └── "postgres" = service name above = hostname

volumes:                 # Named volumes (data persists when container restarts)
  postgres_data:         # Defined here, referenced in services above
```

### Docker Compose networking

All services in the same `docker-compose.yml` are on the same virtual network automatically. They talk to each other using **service names as hostnames**.

```
Your Mac (localhost)
    │
    │ port 5432
    ▼
┌─────────────────────────────────────────┐
│  Docker Network: banking_default        │
│                                         │
│  postgres:5432  ←─── iam-service        │
│  redis:6379     ←─── iam-service        │
│  kafka:9092     ←─── iam-service        │
│                                         │
└─────────────────────────────────────────┘
```

### Essential Docker Compose commands

```bash
docker compose up -d           # Start all services in background
docker compose down            # Stop and remove containers
docker compose down -v         # Also remove volumes (wipes database!)
docker compose ps              # See status of all services
docker compose logs -f kafka   # Follow logs of one service
docker compose restart redis   # Restart one service
docker compose exec postgres psql -U banking  # Shell into a container
```

---

## 4. Why Kubernetes?

Docker Compose is great for local dev. But in production you need:

| Need | Problem with Docker Compose | Kubernetes solution |
|------|-----------------------------|---------------------|
| High availability | One crash = downtime | Auto-restarts crashed pods |
| Scaling | Manual `docker run` more copies | `replicas: 3` — done |
| Rolling updates | Stop old, start new = downtime | Zero-downtime rolling deployments |
| Load balancing | Need nginx config | Built-in Service load balancing |
| Health checks | Limited | Startup / Liveness / Readiness probes |
| Multiple servers | Compose runs on one machine | Runs across a cluster of machines |
| Self-healing | You get paged at 3am | K8s restarts failed containers |
| Resource limits | Runaway app eats all RAM | CPU/memory limits per container |

### The mental model shift

```
Docker Compose mindset:
  "I want to run container X on this machine"

Kubernetes mindset:
  "I want 3 copies of service X running somewhere in my cluster.
   I don't care which machine. Keep them running. Restart if they crash."
```

---

## 5. Kubernetes Core Concepts

### Cluster

A cluster = one or more machines (nodes) managed together.

```
Kubernetes Cluster
├── Control Plane (the brain)
│   ├── API Server     — receives all kubectl commands
│   ├── etcd           — stores all cluster state (database)
│   ├── Scheduler      — decides which node to run a pod on
│   └── Controller     — watches state, fixes drift
│
└── Worker Nodes (the muscles — where your apps run)
    ├── Node 1 (machine)
    │   ├── Pod A
    │   └── Pod B
    ├── Node 2 (machine)
    │   └── Pod C
    └── Node 3 (machine)
        ├── Pod D
        └── Pod E
```

### Pod

The smallest deployable unit in Kubernetes. A pod = one or more containers that:
- Always run on the same node
- Share the same network (same IP)
- Share storage volumes

> Think of a pod like a "logical host" — the containers inside it are like processes on the same machine.

Usually a pod = one container. Multi-container pods are for sidecars (logging agent, proxy, etc.)

### Node

A physical or virtual machine in the cluster. Can be your laptop, a cloud VM, a Raspberry Pi.

### Namespace

A way to divide a cluster into virtual sub-clusters. Like folders.

```bash
# Our project uses the "banking" namespace
kubectl get pods -n banking
kubectl get pods -n argocd

# Without -n you get the "default" namespace
kubectl get pods
```

### Deployment

A `Deployment` manages pods. You tell it:
- What image to run
- How many copies (`replicas`)
- What resources to allocate

The Deployment creates a `ReplicaSet` which creates `Pods`. If a pod crashes, the ReplicaSet creates a new one automatically.

```
Deployment (you manage this)
    └── ReplicaSet (K8s manages this)
            ├── Pod 1 (running)
            ├── Pod 2 (running)
            └── Pod 3 (crashed → K8s creates Pod 4)
```

### Service

A `Service` gives pods a stable network address. Pods have random IPs that change when they restart. A Service has a fixed DNS name and load-balances across all matching pods.

```
Client → kafka:9092 (Service name)
            │
            ├── Pod 1 (10.244.0.5:9092)
            ├── Pod 2 (10.244.0.6:9092)   ← load balanced
            └── Pod 3 (10.244.0.7:9092)
```

### ConfigMap

A `ConfigMap` stores non-secret configuration. Mounted into pods as environment variables or files.

### Secret

A `Secret` stores sensitive data (passwords, tokens). Values are base64-encoded (not encrypted by default — use Vault or Sealed Secrets for production).

---

## 6. Kubernetes YAML — Every Field Explained

### Namespace

```yaml
apiVersion: v1            # Which K8s API version defines this resource
kind: Namespace           # The type of resource
metadata:
  name: banking           # The name — used in -n banking flag
```

### Deployment (fully annotated)

```yaml
apiVersion: apps/v1       # Deployment is in the "apps" API group
kind: Deployment
metadata:
  name: iam-service       # Name of this Deployment object
  namespace: banking      # Which namespace it lives in
  labels:                 # Key-value tags — used for filtering with kubectl
    app: iam-service

spec:                     # The desired state
  replicas: 1             # How many pod copies to run

  selector:               # How the Deployment finds its pods
    matchLabels:
      app: iam-service    # Must match the pod template labels below

  template:               # Template for creating pods
    metadata:
      labels:
        app: iam-service  # MUST match selector.matchLabels above

    spec:                 # The pod specification
      containers:
        - name: iam-service           # Name of the container within the pod
          image: ghcr.io/odyssey-lab-v0/iam-service:abc1234
          # Full image path: registry/org/image:tag
          # CD pipeline updates the tag on every deployment

          ports:
            - containerPort: 8081     # Port the app listens on INSIDE the container
                                      # This is documentation — doesn't open firewall

          envFrom:                    # Load ALL keys from a ConfigMap/Secret as env vars
            - secretRef:
                name: banking-db-secret      # K8s Secret named "banking-db-secret"
            - configMapRef:
                name: iam-service-config     # K8s ConfigMap named "iam-service-config"

          # env:                      # Alternative: set individual env vars
          #   - name: MY_VAR
          #     value: "hello"
          #   - name: DB_PASS
          #     valueFrom:
          #       secretKeyRef:
          #         name: banking-db-secret
          #         key: DB_PASSWORD

          startupProbe:               # Fires FIRST. Liveness/Readiness don't start until this passes.
            httpGet:
              path: /actuator/health/liveness   # HTTP GET to this path
              port: 8081
            initialDelaySeconds: 30   # Wait 30s before first check
            periodSeconds: 10         # Check every 10s
            failureThreshold: 30      # Allow 30 failures = 30×10s = 5 minutes max to start
            # If startup takes > 5 minutes → pod is killed and restarted

          livenessProbe:              # Is the app still alive? (fires after startupProbe passes)
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            periodSeconds: 15         # Check every 15s
            failureThreshold: 3       # 3 failures → kill and restart the container
            # No initialDelaySeconds needed — startupProbe already handled that

          readinessProbe:             # Is the app ready to receive traffic?
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 5
            # Failing readiness ≠ restart. It just removes the pod from load balancer.
            # Good for: app is alive but DB connection isn't ready yet.

          resources:
            requests:                 # Minimum guaranteed resources
              cpu: 100m               # 100 millicores = 0.1 CPU core
              memory: 128Mi           # 128 Mebibytes
            limits:                   # Maximum allowed
              cpu: 500m               # 0.5 CPU core
              memory: 512Mi           # If exceeded → OOMKilled (exit code 137)

# resources explained:
# 1 CPU = 1000m (millicores)
# K8s uses requests to SCHEDULE (find a node with enough space)
# K8s uses limits to ENFORCE (kill if exceeded)
# requests < limits = "burstable" class (our setup)
# requests = limits = "guaranteed" class (best for production)
```

### Service (fully annotated)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: iam-service        # DNS name inside the cluster
  namespace: banking

spec:
  selector:
    app: iam-service       # Route traffic to pods with this label
                           # Must match Deployment pod template labels

  ports:
    - port: 8081           # Port the Service listens on (what clients call)
      targetPort: 8081     # Port on the pod to forward to
      protocol: TCP

  type: ClusterIP          # Default. Only reachable inside the cluster.
  # type: NodePort         # Exposes on every node's IP at a static port (30000-32767)
  # type: LoadBalancer     # Creates cloud load balancer (AWS ELB, GCP LB, etc.)
```

### ConfigMap (fully annotated)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: iam-service-config
  namespace: banking

data:                             # Plain text key-value pairs
  SPRING_PROFILES_ACTIVE: k8s    # Spring Boot profile
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking
  #                                           └── "postgres" = Service name in same namespace
  KAFKA_BROKERS: kafka:9092
  REDIS_HOST: redis
  REDIS_PORT: "6379"             # Values must be strings (quote numbers)
```

### Secret (fully annotated)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: banking-db-secret
  namespace: banking

type: Opaque                     # Generic secret (vs kubernetes.io/tls, etc.)

stringData:                      # Plain text — K8s base64-encodes automatically
  DB_USERNAME: banking
  DB_PASSWORD: supersecret
  DB_URL: jdbc:postgresql://postgres:5432/banking

# Alternative: data (you must base64 encode manually)
# data:
#   DB_PASSWORD: c3VwZXJzZWNyZXQ=   # echo -n "supersecret" | base64
```

### Endpoints (pointing to external service)

```yaml
# Use case: We run Kafka on the host machine.
# We create a K8s Service + Endpoints that routes to it.
# Inside K8s, apps just call "kafka:9092" — they don't know it's outside.

apiVersion: v1
kind: Endpoints
metadata:
  name: kafka              # MUST match the Service name below
  namespace: banking

subsets:
  - addresses:
      - ip: 192.168.65.254  # Host machine IP (Docker Desktop exposes host here)
    ports:
      - port: 9092

---
apiVersion: v1
kind: Service
metadata:
  name: kafka              # MUST match Endpoints name above
  namespace: banking

spec:
  ports:
    - port: 9092
      targetPort: 9092
  # No selector! When there's no selector, K8s uses the Endpoints object above.
```

---

## 7. Kubernetes Networking

### How pods find each other

Every pod gets its own IP. But pod IPs change on restart. That's why Services exist.

```
Within the same namespace:
  postgres-pod-ip: 10.244.0.5  (changes on restart)
  Service "postgres": 10.96.0.100  (never changes)

How iam-service connects to postgres:
  DB_URL: jdbc:postgresql://postgres:5432/banking
                             └── K8s DNS resolves "postgres" → 10.96.0.100
                                 which load-balances to actual pod IP
```

### DNS inside Kubernetes

K8s has built-in DNS. Every Service gets a DNS entry:

```
<service-name>.<namespace>.svc.cluster.local

Examples:
  postgres.banking.svc.cluster.local
  kafka.banking.svc.cluster.local

Within the same namespace, you can use just:
  postgres
  kafka
```

### Service types

```
ClusterIP (default)
  ┌─────────────────┐
  │   K8s Cluster   │
  │                 │
  │  App → Service  │   Only reachable inside the cluster
  │                 │
  └─────────────────┘

NodePort
  ┌─────────────────┐
  │   K8s Cluster   │
  │       ↑         │
  │  NodePort:30080 │   Reachable from outside via <node-ip>:30080
  │       ↓         │
  │     Service     │
  └─────────────────┘

LoadBalancer (cloud only)
  Internet → Cloud LB → NodePort → Service → Pods
  Creates an AWS ELB / GCP Load Balancer automatically

Port-Forward (development only)
  kubectl port-forward svc/zipkin -n banking 9411:9411
  Routes localhost:9411 → zipkin Service inside K8s
  Only works while the command is running
```

---

## 8. Kubernetes Health Probes

Three types of probes. They serve different purposes and work together.

```
Timeline of a pod starting:

t=0s     Pod starts, container launches
         │
t=30s    startupProbe begins checking
         │  (checking every 10s, up to 30 times = 5 minutes)
         │
t=~2min  App finishes booting → startupProbe PASSES
         │
         ├── readinessProbe starts (app ready for traffic?)
         └── livenessProbe starts (app still alive?)
```

### startupProbe

"Give the app time to boot before anyone bothers it."

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 30    # Don't even start checking for 30s
  periodSeconds: 10          # Check every 10s
  failureThreshold: 30       # 30 checks × 10s = 300s (5 min) to start

# While startupProbe hasn't passed yet:
# - livenessProbe does NOT run
# - readinessProbe does NOT run
# - Pod is not killed
# - Pod does not receive traffic
```

### livenessProbe

"Is the app still alive? If not, kill it and restart."

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  periodSeconds: 15
  failureThreshold: 3   # 3 × 15s = 45s of failure before restart

# Spring Boot Actuator: /actuator/health/liveness
# Returns 200 OK if app is alive
# Returns 503 if app is in a broken state (deadlock, etc.)
```

### readinessProbe

"Is the app ready to receive traffic? If not, remove from load balancer."

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 5

# Failing readiness does NOT kill the pod.
# It just stops routing traffic to it.
# Useful during: startup, maintenance, temporary DB connection loss.

# Spring Boot Actuator: /actuator/health/readiness
# Returns 200 OK if all dependencies (DB, Kafka, Redis) are connected
```

### Why exit code 137?

```
Exit code 137 = 128 + 9
                    └── signal 9 = SIGKILL

SIGKILL means the container was forcefully killed.
Causes:
  1. OOMKill — container exceeded memory limit
  2. livenessProbe failed failureThreshold times → K8s sent SIGKILL

How to tell which:
  kubectl describe pod <name> | grep "OOMKilled"
  # "OOMKilled: true" = out of memory
  # "Error" + livenessProbe Unhealthy events = probe failure
```

---

## 9. Kubernetes Storage

### The problem

Containers are stateless. When a container restarts, all files inside are gone. Databases need their data to survive restarts.

### Volume types

```yaml
# EmptyDir — temporary, deleted when pod is removed
volumes:
  - name: cache
    emptyDir: {}

# HostPath — mounts a directory from the node (development only, not portable)
volumes:
  - name: data
    hostPath:
      path: /data/postgres

# PersistentVolumeClaim — the right way for production
volumes:
  - name: data
    persistentVolumeClaim:
      claimName: postgres-pvc
```

### PersistentVolume (PV) and PersistentVolumeClaim (PVC)

```
PersistentVolume (PV)
  = The actual storage (disk on cloud, NFS, local disk)
  = Created by cluster admin

PersistentVolumeClaim (PVC)
  = A request for storage ("I need 10GB")
  = Created by you / your manifest
  = K8s binds it to a matching PV

StatefulSet
  = Like Deployment but each pod gets its own PVC
  = Pods have stable names (postgres-0, postgres-1)
  = Used for databases
```

### StatefulSet for Postgres

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: banking

spec:
  serviceName: postgres        # Headless service name
  replicas: 1

  selector:
    matchLabels:
      app: postgres

  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          env:
            - name: POSTGRES_DB
              value: banking
          volumeMounts:
            - name: postgres-data       # Name of volume (matches below)
              mountPath: /var/lib/postgresql/data   # Where to mount inside container

  volumeClaimTemplates:                 # Each pod replica gets its own PVC
    - metadata:
        name: postgres-data
      spec:
        accessModes: [ReadWriteOnce]    # Only one pod can write at a time
        resources:
          requests:
            storage: 10Gi              # Request 10GB
```

---

## 10. Kubernetes Secrets & ConfigMaps

### ConfigMap — non-sensitive config

```yaml
# Create from YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: iam-service-config
  namespace: banking
data:
  SPRING_PROFILES_ACTIVE: k8s
  KAFKA_BROKERS: kafka:9092

# Create from command line
kubectl create configmap iam-service-config \
  --from-literal=SPRING_PROFILES_ACTIVE=k8s \
  --from-literal=KAFKA_BROKERS=kafka:9092 \
  -n banking
```

### Secret — sensitive config

```yaml
# Create from YAML (stringData — K8s base64 encodes it)
apiVersion: v1
kind: Secret
metadata:
  name: banking-db-secret
  namespace: banking
type: Opaque
stringData:
  DB_PASSWORD: supersecret

# Create from command line (recommended — keeps secret out of Git)
kubectl create secret generic banking-db-secret \
  --from-literal=DB_PASSWORD=supersecret \
  -n banking
```

### Use in a pod

```yaml
# Option 1: Load ALL keys from ConfigMap/Secret as env vars
envFrom:
  - configMapRef:
      name: iam-service-config
  - secretRef:
      name: banking-db-secret

# Option 2: Load specific keys
env:
  - name: DB_PASSWORD           # Env var name in the container
    valueFrom:
      secretKeyRef:
        name: banking-db-secret # Secret name
        key: DB_PASSWORD        # Key within the secret
```

### ImagePullSecret — pulling private images

```bash
# Create the secret
kubectl create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=your-username \
  --docker-password=your-token \
  -n banking

# Option A: Reference in each pod spec
spec:
  imagePullSecrets:
    - name: ghcr-pull-secret

# Option B: Patch default service account (all pods auto-use it)
kubectl patch serviceaccount default -n banking \
  -p '{"imagePullSecrets": [{"name": "ghcr-pull-secret"}]}'
```

---

## 11. K3d — Lightweight Local Kubernetes

### What is K3d?

K3d runs K3s (a lightweight Kubernetes distribution) inside Docker containers. Instead of Docker Desktop's heavy VM, K3s uses ~300MB RAM vs ~2GB.

```
Docker Desktop K8s:       K3d:
┌──────────────────┐      ┌──────────────────┐
│  Heavy Linux VM  │      │ Docker Container │
│  (2GB RAM+)      │      │ (K3s, ~300MB)    │
│  ┌────────────┐  │      │ ┌─────────────┐  │
│  │ Kubernetes │  │      │ │ K3s (light  │  │
│  │  (full)    │  │      │ │ Kubernetes) │  │
│  └────────────┘  │      │ └─────────────┘  │
└──────────────────┘      └──────────────────┘
```

### Install K3d

```bash
# macOS
brew install k3d

# Linux
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
```

### Create a cluster

```bash
# Simple cluster
k3d cluster create odyssey

# With port mappings (expose HTTP/HTTPS to host)
k3d cluster create odyssey \
  --port "80:80@loadbalancer" \        # host:container@which-node
  --port "443:443@loadbalancer" \
  --agents 2                           # 2 worker nodes

# List clusters
k3d cluster list

# Delete cluster
k3d cluster delete odyssey
```

### K3d vs Docker Desktop K8s

| | Docker Desktop K8s | K3d |
|--|-------------------|-----|
| RAM overhead | ~2GB | ~300MB |
| Start time | 2-3 min | ~30 seconds |
| Multi-node | No | Yes |
| ARM64 native | Yes | Yes |
| Built-in load balancer | No | Yes (Traefik) |
| Reset | Restart Docker Desktop | `k3d cluster delete && k3d cluster create` |

### Switching between clusters

```bash
# See all available clusters/contexts
kubectl config get-contexts

# Switch context
kubectl config use-context k3d-odyssey        # K3d cluster
kubectl config use-context docker-desktop     # Docker Desktop cluster

# Check which cluster you're talking to
kubectl cluster-info
```

---

## 12. GitOps — What, Why, How

### The old way (Push model / imperative)

```
Developer → runs scripts → kubectl apply → cluster changes
Developer → CI/CD pipeline → kubectl apply → cluster changes

Problems:
- Who changed what? When? No audit trail.
- Cluster drifts from what's documented
- "Works in staging, not in prod" — different config
- Disaster recovery: "What was the exact state?"
```

### The GitOps way (Pull model / declarative)

```
Git repo = single source of truth for everything

Developer → merges PR → Git changes → ArgoCD detects → syncs cluster

Benefits:
- Full audit trail (git log = deployment history)
- Rollback = git revert
- Cluster always matches Git
- Disaster recovery = point new cluster at same Git repo
```

### GitOps principles

1. **Declarative:** Describe the desired state, not the steps to get there
2. **Versioned:** Everything in Git with full history
3. **Automatic:** Software (ArgoCD) applies changes, not humans
4. **Continuous reconciliation:** ArgoCD constantly checks Git vs cluster, fixes drift

### Declarative vs Imperative

```bash
# IMPERATIVE — tell K8s what to DO
kubectl scale deployment iam-service --replicas=3
kubectl set image deployment/iam-service iam-service=myimage:v2

# DECLARATIVE — tell K8s what the STATE should BE
# Edit deployment.yaml: replicas: 3, image: myimage:v2
kubectl apply -f deployment.yaml
# K8s figures out what changes to make

# GitOps goes further — you don't even run kubectl apply.
# ArgoCD does it for you by watching Git.
```

---

## 13. ArgoCD — GitOps Engine

### What ArgoCD does

ArgoCD runs inside your Kubernetes cluster and:
1. Watches a Git repository for changes
2. Compares what's in Git (desired state) vs what's in the cluster (live state)
3. Automatically syncs if they differ

```
                    ┌─────────────────────────────────────┐
                    │        Kubernetes Cluster           │
                    │                                     │
GitHub Repo ──poll──► ArgoCD ──apply──► banking namespace│
(k8s/*.yaml)        │                                     │
                    │  iam-service  banking-core-service  │
                    │  postgres     redis     kafka       │
                    └─────────────────────────────────────┘
```

### Install ArgoCD

```bash
# Create namespace
kubectl create namespace argocd

# Install ArgoCD
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait until ready
kubectl wait --for=condition=available \
  deployment/argocd-server -n argocd --timeout=120s

# Access the UI (no external IP by default)
kubectl port-forward svc/argocd-server -n argocd 8090:443
# Open https://localhost:8090

# Get initial admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

### ArgoCD Application manifest

An `Application` tells ArgoCD where to find manifests and where to deploy them:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application               # ArgoCD's custom resource type
metadata:
  name: banking-services        # Name shown in ArgoCD UI
  namespace: argocd             # ArgoCD resources always go in argocd namespace

spec:
  project: default              # ArgoCD project (for RBAC — use default for now)

  source:
    repoURL: https://github.com/Odyssey-Lab-V0/odyssey-bank.git
    targetRevision: main        # Which branch/tag/commit to watch
    path: k8s/services          # Which folder in the repo contains manifests
    directory:
      recurse: true             # Also look in subdirectories

  destination:
    server: https://kubernetes.default.svc   # Which cluster (this one)
    namespace: banking          # Deploy into this namespace

  syncPolicy:
    automated:
      prune: true               # Delete K8s resources removed from Git
      selfHeal: true            # Re-sync if someone manually changes the cluster
    syncOptions:
      - CreateNamespace=true    # Create namespace if it doesn't exist
```

### Repo credentials secret

For private GitHub repos, ArgoCD needs credentials:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: odyssey-bank-repo
  namespace: argocd
  labels:
    argocd.argoproj.io/secret-type: repository
    # ↑ CRITICAL: Must be a LABEL (not annotation) for ArgoCD to recognise it

stringData:
  type: git
  url: https://github.com/Odyssey-Lab-V0/odyssey-bank.git
  username: your-github-username
  password: ghp_yourPersonalAccessToken  # PAT with repo + read:packages scope
```

> **Common mistake:** Putting `argocd.argoproj.io/secret-type: repository` as an annotation instead of a label. ArgoCD only scans labels, not annotations. The repo will show as "failed to connect" with no obvious error.

---

## 14. ArgoCD App-of-Apps Pattern

### The problem

If you have one ArgoCD `Application` YAML per service, you need to manually `kubectl apply` each one. That defeats the purpose of GitOps.

### The solution: App-of-Apps

One root `Application` that manages other `Application` objects. You only apply the root manually — once. Everything else is self-managed.

```
kubectl apply -f k8s/argocd/app-of-apps.yaml
                │
                ▼
     banking-platform (root Application)
     watches: k8s/argocd/
                │
       ┌────────┴────────┐
       ▼                 ▼
 banking-infra     banking-services
 watches:          watches:
 k8s/infra/        k8s/services/
       │                 │
  Postgres           iam-service
  Redis              banking-core
  Kafka              onboarding
  Zipkin             kyc, aml
                     notification
```

### Root Application

```yaml
# k8s/argocd/app-of-apps.yaml
# Apply this ONCE manually. Never again.

apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: banking-platform
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
    # When this Application is deleted, also delete all child resources

spec:
  project: default
  source:
    repoURL: https://github.com/Odyssey-Lab-V0/odyssey-bank.git
    targetRevision: main
    path: k8s/argocd           # This folder contains the child Application YAMLs
    directory:
      recurse: false           # Don't recurse — we only want the files in this folder
      exclude: app-of-apps.yaml  # Exclude self to prevent infinite loop

  destination:
    server: https://kubernetes.default.svc
    namespace: argocd          # Child Applications also go in argocd namespace

  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

### Child Application for infrastructure

```yaml
# k8s/argocd/infra-app.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: banking-infra
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io

spec:
  project: default
  source:
    repoURL: https://github.com/Odyssey-Lab-V0/odyssey-bank.git
    targetRevision: main
    path: k8s/infra            # Deploys postgres, redis, kafka, zipkin

  destination:
    server: https://kubernetes.default.svc
    namespace: banking

  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

### Child Application for services

```yaml
# k8s/argocd/services-app.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: banking-services
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io

spec:
  project: default
  source:
    repoURL: https://github.com/Odyssey-Lab-V0/odyssey-bank.git
    targetRevision: main
    path: k8s/services
    directory:
      recurse: true            # Picks up iam-service/, kyc-service/, etc.

  destination:
    server: https://kubernetes.default.svc
    namespace: banking

  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

---

## 15. CI/CD Pipeline End to End

### The full flow

```
Developer pushes code to GitHub
         │
         ▼
┌─────────────────────────────┐
│  CI: ci.yml                 │
│  Trigger: push / PR to main │
│                             │
│  1. Checkout code           │
│  2. Java 21 + Maven cache   │
│  3. mvn install -DskipTests │
│  4. mvn test                │
│     └── ArchUnit (DDD)      │
│     └── Unit tests          │
│  5. Upload reports if fail  │
└─────────────────────────────┘
         │ (if tests pass)
         ▼
┌─────────────────────────────┐
│  CD: cd.yml                 │
│  Trigger: push to main only │
│                             │
│  Job 1: build-images        │
│  (6 services in parallel)   │
│                             │
│  For each service:          │
│  1. docker buildx build     │
│     --platform amd64,arm64  │
│  2. Push to ghcr.io         │
│     tag: :abc1234 (sha)     │
│     tag: :latest            │
│                             │
│  Job 2: update-manifests    │
│  (after all images pushed)  │
│  1. sed image tags in       │
│     k8s/services/*/         │
│     deployment.yaml         │
│  2. git commit [skip ci]    │
│  3. git push                │
└─────────────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  ArgoCD (in cluster)        │
│  Polls GitHub every 3 min   │
│                             │
│  Detects new commit         │
│  Diffs: Git vs cluster      │
│  Applies changed manifests  │
│                             │
│  K8s does rolling update:   │
│  1. Start new pod           │
│  2. Wait for startupProbe   │
│  3. Wait for readinessProbe │
│  4. Route traffic to new    │
│  5. Terminate old pod       │
└─────────────────────────────┘
```

### Multi-arch images (amd64 + arm64)

```yaml
# In cd.yml
- name: Build and push
  uses: docker/build-push-action@v5
  with:
    platforms: linux/amd64,linux/arm64
    # ↑ Builds for Intel (CI runners, x86 servers) AND
    #   Apple Silicon / Oracle OKE ARM nodes
    # Docker pulls the right arch automatically
```

Why this matters:
- GitHub Actions runners are `linux/amd64` (Intel)
- Docker Desktop on M1/M2 Mac = `arm64`
- Oracle OKE Free Tier = `arm64` (Ampere)
- Without multi-arch: image built on Intel can't run natively on ARM (slow emulation or crash)

### The [skip ci] trick

```yaml
git commit -m "ci: update image tags to abc1234 [skip ci]"
```

After the CD pipeline pushes updated deployment.yaml files, it creates a new commit on `main`. Without `[skip ci]`, this would trigger CI again → CI triggers CD → CD pushes → triggers CI... infinite loop.

GitHub Actions recognises `[skip ci]` in the commit message and skips all workflows.

---

## 16. Real World Gotchas

### 1. Docker image names must be lowercase

```bash
# WRONG — causes InvalidImageName error in K8s
ghcr.io/Odyssey-Lab-V0/iam-service:latest

# CORRECT
ghcr.io/odyssey-lab-v0/iam-service:latest

# GitHub org names are case-insensitive, but Docker image names are not.
# Fix in CI/CD:
run: echo "value=ghcr.io/$(echo '${{ github.repository_owner }}' | tr '[:upper:]' '[:lower:]')" >> $GITHUB_OUTPUT
```

### 2. ArgoCD repo secret must use labels, not annotations

```yaml
# WRONG — ArgoCD ignores this
metadata:
  annotations:
    argocd.argoproj.io/secret-type: repository

# CORRECT — ArgoCD scans labels
metadata:
  labels:
    argocd.argoproj.io/secret-type: repository
```

### 3. Maven multi-module Docker build

```dockerfile
# WRONG — fails because root pom.xml references all modules
COPY pom.xml .
COPY iam-service/pom.xml iam-service/
RUN mvn dependency:go-offline -pl iam-service -am
# Error: Module 'kyc-service' not found

# CORRECT — copy ALL module pom.xml files first
COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY iam-service/pom.xml iam-service/
COPY banking-core-service/pom.xml banking-core-service/
# ... ALL modules listed in root pom.xml <modules>
RUN mvn dependency:go-offline -pl iam-service -am
```

### 4. Spring Boot 3.x tracing — tracer.currentSpan() returns null

```java
// WRONG in Spring Boot 3.x — returns null during HTTP requests
Span span = tracer.currentSpan();  // null!

// CORRECT — HTTP spans live in ObservationRegistry
var obs = observationRegistry.getCurrentObservation();
var ctx = obs.getContextView().get(TracingObservationHandler.TracingContext.class);
String traceId = ctx.getSpan().context().traceId();
```

### 5. OOMKill (exit code 137) on arm64

Spring Boot takes more memory to start on arm64 emulation. Solutions:
- Set container memory limit ≥ 512Mi
- Add `startupProbe` to give 5 minutes before liveness kicks in
- Use native arm64 (Oracle OKE, M-series Mac without emulation)
- Set `-XX:MaxRAMPercentage=75.0` so JVM respects container limits

### 6. ArchUnit — wrong method causes 989 violations

```java
// WRONG — flags Spring annotations, JDK internals as violations
layeredArchitecture()
    .consideringAllDependencies()  // too strict

// CORRECT — only checks dependencies between your layers
layeredArchitecture()
    .consideringOnlyDependenciesInLayers()
```

### 7. K8s Endpoints for host services

When you need K8s pods to reach a service running on your host machine (like local Kafka):

```bash
# Find Docker Desktop host IP
kubectl run -it --rm test --image=busybox -- nslookup host.docker.internal
# Returns: 192.168.65.254 (typical Docker Desktop host IP)
```

```yaml
# Create manual Endpoints + Service
apiVersion: v1
kind: Endpoints
metadata:
  name: kafka         # Must match Service name
subsets:
  - addresses:
      - ip: 192.168.65.254   # Host machine IP
    ports:
      - port: 9092

---
apiVersion: v1
kind: Service
metadata:
  name: kafka         # Must match Endpoints name
spec:
  ports:
    - port: 9092
  # No selector = K8s uses the manual Endpoints above
```

### 8. PAT scopes for ghcr.io + ArgoCD

A GitHub Personal Access Token needs these scopes:
- `repo` — ArgoCD can read private repos
- `read:packages` — K8s can pull images from ghcr.io
- `write:packages` — GitHub Actions can push images to ghcr.io

---

## Quick Reference Card

```bash
# ── Cluster ─────────────────────────────────────────────
kubectl cluster-info                       # Which cluster am I on?
kubectl config get-contexts                # List all clusters
kubectl config use-context docker-desktop  # Switch cluster

# ── Namespaces ───────────────────────────────────────────
kubectl get namespaces
kubectl create namespace banking

# ── Pods ─────────────────────────────────────────────────
kubectl get pods -n banking                # List pods
kubectl get pods -n banking -w             # Watch (live updates)
kubectl describe pod <name> -n banking     # Full details + events
kubectl logs <pod-name> -n banking         # Logs
kubectl logs <pod-name> -n banking -f      # Follow logs
kubectl logs <pod-name> -n banking --previous  # Logs from crashed container
kubectl exec -it <pod-name> -n banking -- sh   # Shell into pod
kubectl delete pod <pod-name> -n banking   # Delete (deployment recreates it)

# ── Deployments ──────────────────────────────────────────
kubectl get deployments -n banking
kubectl rollout restart deployment/iam-service -n banking  # Force redeploy
kubectl rollout status deployment/iam-service -n banking   # Watch rollout

# ── Services ─────────────────────────────────────────────
kubectl get services -n banking
kubectl port-forward svc/zipkin -n banking 9411:9411  # Access locally

# ── Apply / Delete ───────────────────────────────────────
kubectl apply -f manifest.yaml             # Create or update
kubectl delete -f manifest.yaml            # Delete
kubectl apply -f k8s/                      # Apply all files in directory
kubectl apply -f k8s/ --recursive          # Apply all files recursively

# ── ArgoCD ───────────────────────────────────────────────
kubectl get applications -n argocd         # List ArgoCD apps
kubectl describe application banking-services -n argocd  # Debug sync issues

# ── K3d ─────────────────────────────────────────────────
k3d cluster create odyssey                 # Create cluster
k3d cluster list                           # List clusters
k3d cluster delete odyssey                 # Delete cluster

# ── Debug ────────────────────────────────────────────────
kubectl describe pod <name> -n banking | grep -A10 Events  # See what happened
kubectl top pods -n banking                # CPU/memory usage (if metrics-server installed)
kubectl get events -n banking --sort-by=.lastTimestamp     # Recent events
```
