# Odyssey Bank — Platform Architecture & Setup Guide

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Repository Structure](#2-repository-structure)
3. [Services & Ports](#3-services--ports)
4. [Technology Stack](#4-technology-stack)
5. [Architecture Decisions](#5-architecture-decisions)
6. [Local Development (Docker Compose)](#6-local-development-docker-compose)
7. [Building & Testing](#7-building--testing)
8. [Docker Images (Multi-Stage Builds)](#8-docker-images-multi-stage-builds)
9. [Kubernetes Manifests](#9-kubernetes-manifests)
10. [GitOps with ArgoCD](#10-gitops-with-argocd)
11. [CI/CD Pipeline (GitHub Actions)](#11-cicd-pipeline-github-actions)
12. [Distributed Tracing (Zipkin + Micrometer)](#12-distributed-tracing-zipkin--micrometer)
13. [Transactional Outbox Pattern](#13-transactional-outbox-pattern)
14. [Secrets Management](#14-secrets-management)
15. [End-to-End Flow: Code → Production](#15-end-to-end-flow-code--production)
16. [Troubleshooting](#16-troubleshooting)
17. [Platform Limitations & Next Steps](#17-platform-limitations--next-steps)

---

## 1. Project Overview

**Odyssey Bank** is a production-grade Core Digital Banking Platform built with Domain-Driven Design (DDD), microservices, and a full GitOps delivery pipeline.

| Attribute | Value |
|-----------|-------|
| GitHub Org | `Odyssey-Lab-V0` |
| Repository | `https://github.com/Odyssey-Lab-V0/odyssey-bank.git` |
| Container Registry | `ghcr.io/odyssey-lab-v0/` |
| Architecture | Microservices, Event-Driven, DDD |
| Java Version | 21 (Temurin) |
| Framework | Spring Boot 3.2.5 |
| Build Tool | Maven (multi-module reactor) |

---

## 2. Repository Structure

```
banking-platform/
├── pom.xml                          # Root Maven reactor (parent POM)
├── docker-compose.yml               # Local development stack
├── banking-shared/                  # Shared domain primitives & utilities
├── iam-service/                     # Identity & Access Management (port 8081)
├── banking-core-service/            # Core banking — accounts & transactions (port 8082)
├── onboarding-service/              # Customer onboarding (port 8083)
├── kyc-service/                     # Know Your Customer (port 8084)
├── aml-service/                     # Anti-Money Laundering (port 8085)
├── notification-service/            # Email / SMS notifications (port 8086)
├── k8s/
│   ├── namespace.yaml               # banking namespace
│   ├── argocd/
│   │   ├── app-of-apps.yaml         # Root ArgoCD Application
│   │   ├── infra-app.yaml           # Child: infra (postgres, kafka, redis, zipkin)
│   │   └── services-app.yaml        # Child: all 6 microservices
│   ├── infra/
│   │   ├── postgres.yaml
│   │   ├── redis.yaml
│   │   ├── kafka.yaml               # Points to host Kafka via Endpoints
│   │   └── zipkin.yaml
│   └── services/
│       ├── shared-secrets.yaml      # DB + JWT secrets
│       └── {service}/
│           ├── deployment.yaml
│           ├── service.yaml
│           └── configmap.yaml
├── scripts/
│   └── init-schemas.sql             # DB schema initialisation
└── .github/
    └── workflows/
        ├── ci.yml                   # Build + test on every PR / push
        └── cd.yml                   # Build Docker images + update K8s manifests
```

---

## 3. Services & Ports

| Service | Port (local) | Port (K8s) | Responsibility |
|---------|-------------|------------|----------------|
| `iam-service` | 8082 | 8081 | JWT auth, user registration, sessions, roles |
| `banking-core-service` | — | 8082 | Accounts, transactions, ledger |
| `onboarding-service` | — | 8083 | Customer onboarding workflow |
| `kyc-service` | — | 8084 | Identity verification |
| `aml-service` | — | 8085 | Transaction monitoring, fraud detection |
| `notification-service` | — | 8086 | Email/SMS delivery |
| PostgreSQL | 5432 | 5432 | Primary database (all services share one DB, separate schemas) |
| Redis | 6379 | 6379 | Session cache, rate limiting |
| Kafka | 9092 | 9092 (host) | Async event bus between services |
| Zipkin | 9411 | 9411 | Distributed trace collection UI |
| Kafka UI | 9090 | — | Local dev only |
| Mailhog | 8025 | — | Local email capture |

---

## 4. Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| ORM | Spring Data JPA (Hibernate) |
| Messaging | Apache Kafka (Confluent) |
| Cache | Redis (Spring Data Redis) |
| Database | PostgreSQL 16 |
| Tracing | Micrometer Tracing + Zipkin (B3 propagation) |
| Auth | JWT (HS256) |
| Build | Maven 3.9.7 (multi-module reactor) |
| Containerisation | Docker (multi-stage builds) |
| Container Registry | GitHub Container Registry (ghcr.io) |
| Orchestration | Kubernetes (Docker Desktop locally) |
| GitOps | ArgoCD (App-of-Apps pattern) |
| CI | GitHub Actions |
| Architecture | DDD (Domain-Driven Design) layered architecture |
| Architecture Tests | ArchUnit |

---

## 5. Architecture Decisions

### DDD Layered Architecture
Each service enforces strict layer boundaries with ArchUnit tests:

```
API Layer         → receives HTTP, calls Application layer only
Application Layer → orchestrates use cases, calls Domain + Infrastructure
Domain Layer      → pure business logic, no Spring/infra dependencies
Infrastructure    → JPA repositories, Kafka producers, Redis clients
```

**Rule enforced at compile time:** API cannot access Infrastructure directly. Domain cannot access anything external.

### Transactional Outbox Pattern
To guarantee exactly-once Kafka delivery without distributed transactions:

1. HTTP request arrives → Application service writes event to `outbox_events` table **in the same DB transaction** as the business operation
2. `trace_id` is captured at request time via `ObservationRegistry` and stored in the outbox row
3. A relay poller reads unprocessed outbox rows and publishes to Kafka, injecting the original `trace_id` as B3 headers
4. Kafka consumers read events with full trace context → Zipkin shows end-to-end traces spanning HTTP → DB → Kafka → consumer

### Why `ObservationRegistry` (not `Tracer`) in Spring Boot 3.x
Spring Boot 3.x manages HTTP spans via `ObservationRegistry`, not directly via the `Tracer` bean. Calling `tracer.currentSpan()` during an HTTP request returns `null`. The correct pattern:

```java
var obs = observationRegistry.getCurrentObservation();
TracingObservationHandler.TracingContext ctx =
    obs.getContextView().get(TracingObservationHandler.TracingContext.class);
String traceId = ctx.getSpan().context().traceId();
```

---

## 6. Local Development (Docker Compose)

### Prerequisites
- Docker Desktop ≥ 4.x
- Java 21 (for running services outside Docker)
- Maven 3.9+

### Start the full local stack

```bash
# Start all infrastructure (Postgres, Redis, Kafka, Zipkin, Mailhog)
docker compose up -d

# Verify everything is healthy
docker compose ps
```

### Local service URLs

| Service | URL |
|---------|-----|
| Kafka UI | http://localhost:9090 |
| Zipkin | http://localhost:9411 |
| Mailhog (email) | http://localhost:8025 |
| IAM Service | http://localhost:8082 |

### Run a service locally (outside Docker)

```bash
cd iam-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 7. Building & Testing

### Build all modules

```bash
# From the root — builds all 7 modules in reactor order
mvn install -DskipTests
```

### Run tests

```bash
# All tests including ArchUnit DDD layer checks
mvn test -Dsurefire.failIfNoSpecifiedTests=false
```

### Key test: DDD Architecture (ArchUnit)

Located at `iam-service/src/test/java/com/banking/iam/api/DddArchitectureTest.java`

```java
layeredArchitecture()
    .consideringOnlyDependenciesInLayers()  // ignores Spring internals
    .layer("API").definedBy("com.banking.iam.api..")
    .layer("Application").definedBy("com.banking.iam.application..")
    .layer("Domain").definedBy("com.banking.iam.domain..")
    .layer("Infrastructure").definedBy("com.banking.iam.infrastructure..")
    .whereLayer("API").mayOnlyAccessLayers("Application")
    .whereLayer("Application").mayOnlyAccessLayers("Domain", "Infrastructure")
    .whereLayer("Domain").mayNotAccessAnyLayer()
```

> **Important:** Use `consideringOnlyDependenciesInLayers()` not `consideringAllDependencies()`. The latter flags Spring framework annotations as violations (989 false positives).

---

## 8. Docker Images (Multi-Stage Builds)

Each service has its own `Dockerfile` in `{service}/Dockerfile`. All Dockerfiles follow the same pattern:

```dockerfile
# Stage 1: Build — Maven with full JDK
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy ALL module pom.xml files — Maven reactor requires them all to resolve
COPY pom.xml .
COPY banking-shared/pom.xml banking-shared/
COPY iam-service/pom.xml iam-service/
COPY banking-core-service/pom.xml banking-core-service/
COPY onboarding-service/pom.xml onboarding-service/
COPY kyc-service/pom.xml kyc-service/
COPY aml-service/pom.xml aml-service/
COPY notification-service/pom.xml notification-service/

# Download deps as a cached layer (only invalidated when pom.xml changes)
RUN mvn dependency:go-offline -pl banking-shared,iam-service -am -q

# Copy source and build
COPY banking-shared/src banking-shared/src
COPY iam-service/src iam-service/src
RUN mvn package -pl iam-service -am -DskipTests -q

# Stage 2: Runtime — slim JRE only (~150MB final image)
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S banking && adduser -S banking -G banking
USER banking

# JVM tuned for containers: respects cgroup memory limits, uses ZGC
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

COPY --from=builder /build/iam-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Key design decisions:**
- `-XX:+UseContainerSupport` — JVM reads cgroup memory limits, not host RAM
- `-XX:MaxRAMPercentage=75.0` — JVM heap = 75% of container memory limit
- `-XX:+UseZGC` — low-latency GC suited for banking workloads
- All `pom.xml` files copied before source — Docker layer cache avoids re-downloading deps on source-only changes

### Build an image locally

```bash
# Run from the repo root (build context must include all modules)
docker build -f iam-service/Dockerfile -t iam-service:local .
```

---

## 9. Kubernetes Manifests

All manifests live under `k8s/` and are managed by ArgoCD.

### Namespace

```bash
kubectl apply -f k8s/namespace.yaml
```

Creates the `banking` namespace.

### Infrastructure (`k8s/infra/`)

| Manifest | What it deploys |
|----------|----------------|
| `postgres.yaml` | PostgreSQL 16 StatefulSet with persistent volume |
| `redis.yaml` | Redis 7 Deployment |
| `kafka.yaml` | Kubernetes `Endpoints` + `Service` pointing to host machine Kafka (`192.168.65.254:9092`) |
| `zipkin.yaml` | Zipkin all-in-one Deployment |

> **Note on Kafka:** Rather than running Kafka inside K8s (which caused CrashLoopBackOff due to resource constraints on local clusters), the K8s `kafka` Service is wired to the host machine's Kafka via a manual `Endpoints` object. This is only needed for local development — on a cloud cluster, a proper Kafka deployment or managed service is used.

### Service Manifests (`k8s/services/{service}/`)

Each service has three files:

**deployment.yaml** — key sections:
```yaml
spec:
  containers:
    - image: ghcr.io/odyssey-lab-v0/iam-service:<SHA>  # CD pipeline updates this
      startupProbe:               # gives Spring Boot up to 5 minutes to start
        httpGet:
          path: /actuator/health/liveness
          port: 8081
        initialDelaySeconds: 30
        periodSeconds: 10
        failureThreshold: 30      # 30 × 10s = 5 minutes max
      livenessProbe:              # only fires AFTER startupProbe succeeds
        httpGet:
          path: /actuator/health/liveness
          port: 8081
        periodSeconds: 15
      resources:
        requests:
          cpu: 100m
          memory: 128Mi
        limits:
          cpu: 500m
          memory: 512Mi           # JVM self-limits to 384Mi (75% of 512Mi)
```

> **Why `startupProbe`?** Spring Boot 3.x with JPA + Redis + Kafka takes 2-3 minutes to start on arm64 emulation. Without a `startupProbe`, the `livenessProbe` fires before the app is ready and kills it in a restart loop.

**configmap.yaml** — Spring profile and app config:
```yaml
data:
  SPRING_PROFILES_ACTIVE: k8s
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking
  KAFKA_BROKERS: kafka:9092
  REDIS_HOST: redis
```

**service.yaml** — ClusterIP Service exposing the actuator port.

### Shared Secrets (`k8s/services/shared-secrets.yaml`)

```bash
# Apply manually — never commit actual secret values to Git
kubectl apply -f k8s/services/shared-secrets.yaml
```

Contains:
- `banking-db-secret` — `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `banking-jwt-secret` — `JWT_SECRET`

---

## 10. GitOps with ArgoCD

### What is GitOps?
Git is the single source of truth. ArgoCD (running in the cluster) **pulls** from GitHub and automatically syncs the cluster state to match the manifests in `main`. No `kubectl apply` in CI — the CD pipeline only updates Git, ArgoCD does the rest.

```
Developer pushes code
       ↓
GitHub Actions CI builds & tests
       ↓
GitHub Actions CD builds Docker image → pushes to ghcr.io
       ↓
CD pipeline updates image tag in k8s/services/*/deployment.yaml → commits to Git
       ↓
ArgoCD detects Git change → syncs cluster → rolls out new pods
```

### App-of-Apps Pattern

ArgoCD manages one root `Application` (`banking-platform`) which in turn manages two child Applications:

```
banking-platform (root)
├── banking-infra    → k8s/infra/     (Postgres, Redis, Kafka, Zipkin)
└── banking-services → k8s/services/  (all 6 microservices)
```

This means a single `kubectl apply` bootstraps everything:

```bash
kubectl apply -f k8s/argocd/app-of-apps.yaml -n argocd
```

### Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD to be ready
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=120s
```

### Store GitHub credentials in ArgoCD

ArgoCD needs to pull from the private GitHub repo. Store a Personal Access Token (PAT) as a K8s secret with the special ArgoCD label:

```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: odyssey-bank-repo
  namespace: argocd
  labels:
    argocd.argoproj.io/secret-type: repository   # MUST be a label, not annotation
stringData:
  type: git
  url: https://github.com/Odyssey-Lab-V0/odyssey-bank.git
  username: Odyssey-Lab-V0
  password: <GITHUB_PAT>
EOF
```

> **Required PAT scopes:** `repo`, `read:packages`, `write:packages`

### Access the ArgoCD UI

```bash
# Port-forward (ArgoCD has no external LoadBalancer by default)
kubectl port-forward svc/argocd-server -n argocd 8090:443

# Get the auto-generated admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

Open **https://localhost:8090** → username `admin` → password from above.

### Bootstrap the platform (first time only)

```bash
# 1. Create namespace
kubectl apply -f k8s/namespace.yaml

# 2. Apply shared secrets (DB creds, JWT secret)
kubectl apply -f k8s/services/shared-secrets.yaml

# 3. Create ghcr.io pull secret so K8s can pull private images
kubectl create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=Odyssey-Lab-V0 \
  --docker-password=<GITHUB_PAT> \
  --docker-email=<EMAIL> \
  -n banking

# 4. Patch default service account to auto-use pull secret on every pod
kubectl patch serviceaccount default -n banking \
  -p '{"imagePullSecrets": [{"name": "ghcr-pull-secret"}]}'

# 5. Deploy the App-of-Apps — ArgoCD takes it from here
kubectl apply -f k8s/argocd/app-of-apps.yaml -n argocd
```

ArgoCD will auto-sync and deploy all infra and services within ~2 minutes.

---

## 11. CI/CD Pipeline (GitHub Actions)

### CI Pipeline (`.github/workflows/ci.yml`)

**Triggers:** every PR to `main`, every push to `main`

**Steps:**
1. Checkout code
2. Set up Java 21 (Temurin) with Maven cache
3. `mvn install -DskipTests -q` — compiles all 7 modules
4. `mvn test` — runs unit + ArchUnit tests
5. On failure: uploads Surefire XML reports as artifacts

### CD Pipeline (`.github/workflows/cd.yml`)

**Triggers:** every push to `main`

**Jobs:**

**`build-images`** (runs 6 in parallel via matrix strategy):
1. Checkout
2. Compute lowercase image prefix: `ghcr.io/odyssey-lab-v0/` (GitHub org names can be mixed case but Docker image names must be lowercase)
3. Login to `ghcr.io` using `GITHUB_TOKEN`
4. Build + push Docker image with two tags:
   - `ghcr.io/odyssey-lab-v0/{service}:<short-sha>` (immutable, traceable)
   - `ghcr.io/odyssey-lab-v0/{service}:latest`
5. Platforms: **`linux/amd64,linux/arm64`** (multi-arch — works on Intel CI runners and Apple Silicon / Oracle OKE arm64 nodes)
6. GitHub Actions cache speeds up subsequent builds

**`update-manifests`** (runs after all 6 images are pushed):
1. Compute the same short SHA
2. `sed` replaces the image tag in all 6 `k8s/services/*/deployment.yaml` files
3. Commits back to `main` with `[skip ci]` to prevent CI re-trigger
4. ArgoCD detects the new commit and rolls out the updated images

> **End result:** A developer merges a PR → within ~15 minutes the new code is running in the cluster. Zero manual steps.

---

## 12. Distributed Tracing (Zipkin + Micrometer)

### How traces flow

```
HTTP Request → IAM Service
    │  (trace_id captured via ObservationRegistry)
    ├── Writes to DB (outbox_events.trace_id = current trace)
    │
    └── Outbox Relay polls DB → publishes to Kafka
            B3 headers injected:
            X-B3-TraceId: <original trace_id>
            X-B3-SpanId:  <new span_id>
            X-B3-Sampled: 1
                │
                └── Consumer service reads Kafka message
                        Span linked to original trace → Zipkin shows full chain
```

### View traces

```bash
# Port-forward Zipkin
kubectl port-forward svc/zipkin -n banking 9411:9411
```

Open **http://localhost:9411** → search by service name or trace ID.

### Key implementation detail (Spring Boot 3.x)

In Spring Boot 3.x, HTTP request spans are created by `ObservationRegistry`, **not** by the `Tracer` bean. `tracer.currentSpan()` returns `null` during an HTTP request. The correct way to extract the trace ID:

```java
@Autowired ObservationRegistry observationRegistry;

private String currentTraceId() {
    var obs = observationRegistry.getCurrentObservation();
    if (obs != null) {
        var ctx = obs.getContextView()
            .get(TracingObservationHandler.TracingContext.class);
        if (ctx != null && ctx.getSpan() != null) {
            return ctx.getSpan().context().traceId();
        }
    }
    // Fallbacks: MDC, then direct Tracer
    String mdcId = MDC.get("traceId");
    if (mdcId != null) return mdcId;
    var span = tracer.currentSpan();
    return span != null ? span.context().traceId() : null;
}
```

---

## 13. Transactional Outbox Pattern

### Problem it solves
Writing to the DB and publishing to Kafka in the same request without a distributed transaction. If Kafka is down, messages must not be lost. If the DB write fails, no message should be published.

### Implementation
1. **Same transaction:** The application service writes the business entity AND an `outbox_events` row in a single `@Transactional` method.
2. **Outbox schema:**
   ```sql
   CREATE TABLE outbox_events (
     id          UUID PRIMARY KEY,
     aggregate   VARCHAR(100),
     event_type  VARCHAR(100),
     payload     JSONB,
     trace_id    VARCHAR(64),    -- captured at HTTP request time
     processed   BOOLEAN DEFAULT false,
     created_at  TIMESTAMP
   );
   ```
3. **Relay:** A scheduled poller (`@Scheduled`) reads `WHERE processed = false`, publishes to Kafka with B3 trace headers, then marks `processed = true`.
4. **Idempotency:** Kafka consumers deduplicate using the event `id`.

---

## 14. Secrets Management

### Local development
Secrets are in `docker-compose.yml` environment variables — acceptable for local only, never committed to production branches.

### Kubernetes
Secrets are stored as K8s `Secret` objects, referenced via `envFrom.secretRef` in deployments. The secrets themselves are **not** in Git — they are applied manually on first cluster setup:

```bash
kubectl apply -f k8s/services/shared-secrets.yaml
```

The `shared-secrets.yaml` file in Git contains placeholder values and must be filled in before applying.

### GitHub Actions
The CD pipeline uses `GITHUB_TOKEN` (auto-provided by GitHub Actions) for image push — no manual secret needed for CI.

For ArgoCD repo access, a PAT is stored as a K8s Secret in the `argocd` namespace (see Section 10).

---

## 15. End-to-End Flow: Code → Production

```
1. Developer pushes feature branch
         │
         ▼
2. PR opened → CI runs (build + test + ArchUnit)
         │
         ▼
3. PR merged to main → CI runs again
         │
         ▼
4. CD triggered:
   - 6 Docker images built in parallel (linux/amd64 + linux/arm64)
   - Images pushed to ghcr.io/odyssey-lab-v0/
   - k8s/services/*/deployment.yaml image tags updated
   - Commit pushed to main with [skip ci]
         │
         ▼
5. ArgoCD detects new commit in main (~2 min poll)
         │
         ▼
6. ArgoCD syncs:
   - Compares live cluster state vs Git
   - Applies changed deployment.yaml files
   - K8s performs rolling update (zero-downtime)
         │
         ▼
7. New pods start:
   - startupProbe gives 5 minutes to boot
   - livenessProbe monitors after startup
   - readinessProbe gates traffic until healthy
         │
         ▼
8. Traffic routed to new pods ✅
```

---

## 16. Troubleshooting

### Services won't start (OOMKilled / exit code 137)
Spring Boot 3.x + JPA takes 2-3 minutes and ~400MB to start. Common causes:

| Symptom | Cause | Fix |
|---------|-------|-----|
| Exit code 137 | OOMKill — container memory limit too low | Increase limit to 512Mi minimum; JVM auto-caps at 75% via `MaxRAMPercentage` |
| Killed before app starts | `livenessProbe` fires too early | Use `startupProbe` with `failureThreshold: 30` (5 min grace) |
| Slow JPA scan (90+ seconds) | arm64 emulation overhead | Run on native arm64 (Oracle OKE) or native amd64 |

### ImagePullBackOff
```bash
kubectl describe pod <pod-name> -n banking | grep -i "failed\|error"
```
Common causes:
- `ghcr.io` packages are private → ensure `ghcr-pull-secret` exists and is on the service account
- Image tag doesn't exist → check if CD pipeline pushed successfully
- Wrong image name case → must be lowercase: `ghcr.io/odyssey-lab-v0/` not `ghcr.io/Odyssey-Lab-V0/`

### ArgoCD not syncing
```bash
kubectl get applications -n argocd
kubectl describe application banking-services -n argocd
```
Common causes:
- Repo secret has wrong label (must be `labels:` not `annotations:`)
- PAT expired or missing `repo` scope
- `repoURL` mismatch (must match exactly)

### Kafka connectivity from K8s
The `kafka` Service in K8s uses a manual `Endpoints` object pointing to `192.168.65.254:9092` (Docker Desktop host IP). If the host IP changes, update `k8s/infra/kafka.yaml` and re-apply.

```bash
# Verify K8s can reach host Kafka
kubectl run -it --rm --restart=Never test --image=busybox -n banking \
  -- nc -zv kafka 9092
```

### ArchUnit test failures
- `consideringAllDependencies()` — use `consideringOnlyDependenciesInLayers()` instead (avoids Spring framework false positives)
- `api_must_not_access_domain_directly` — ensure API layer only injects Application services, never Domain or Infrastructure beans directly

---

## 17. Platform Limitations & Next Steps

### Current Limitations
| Limitation | Impact |
|------------|--------|
| Local K8s on 8GB Mac | Memory pressure — services OOMKill on arm64 emulation |
| Kafka via host Endpoints | Not portable — IP hardcoded for Docker Desktop |
| Single-node K8s | No HA, no node failure tolerance |
| Shared DB (one schema per service) | Not true microservice DB isolation |
| No Ingress / API Gateway | Services not externally accessible without port-forwarding |
| No TLS | HTTP only in-cluster |
| Secrets in manual kubectl apply | No secrets manager (Vault / AWS Secrets Manager) |

### Recommended Next Steps

**1. Move to Oracle OKE Free Tier (immediate)**
- 2 × arm64 nodes, 12GB RAM each = 24GB total
- Native arm64 — no emulation, fast Spring Boot startup
- Always free (not trial credit)
- Your existing GitHub Actions pipeline and ArgoCD config work unchanged — just swap the kubeconfig

**2. Add Ingress**
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml
```

**3. Move Kafka to cluster**
Replace the host-Endpoints hack with a proper Kafka deployment (Strimzi operator or bitnami/kafka with KRaft mode).

**4. Add Vault for secrets**
Replace manual `kubectl apply` of secrets with HashiCorp Vault or External Secrets Operator pulling from AWS Secrets Manager.

**5. Add API Gateway**
Route all external traffic through a single gateway (Spring Cloud Gateway or Kong) for auth, rate limiting, and routing.
