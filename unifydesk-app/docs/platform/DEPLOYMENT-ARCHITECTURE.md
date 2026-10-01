# Deployment Architecture

Baseline: `dev@d9717f93b3e10a308da9636750a4df6926bc6016`

## Current repository-defined topology

The checked-in Kubernetes path targets a small k3s/Traefik deployment:

```text
Internet / external TLS path
        |
     Traefik
   /          \
app.unifydesk.in   api.unifydesk.in
       |                 |
  frontend svc      backend svc
  nginx/React       Spring Boot
       |          /      |       \
       |     PostgreSQL  Redis   Kafka
       |          |
       +------ provider APIs / object storage
```

Ingress exposes `app.unifydesk.in` and `api.unifydesk.in`. The manifest itself contains no `tls:` block, so TLS is an external deployment assumption, not something established by this repository manifest.

## Workloads at the baseline

| Component | Kind / replicas | Persistence | Image behavior | Notes |
|---|---|---|---|---|
| Backend | StatefulSet / 1 | business state in PostgreSQL | `messaging-backend:latest`, pull Never | ordinal supplies Snowflake node id; TCP probes |
| Frontend | Deployment / 1 | stateless | `messaging-frontend:latest` | Recreate strategy; TCP probes |
| PostgreSQL | Deployment / 1 | 5 Gi PVC | `postgres:18.6-alpine` | Recreate strategy |
| Redis | Deployment / 1 | no PVC in manifest | `redis:8.10.1-alpine` | AOF enabled but pod filesystem is ephemeral |
| Kafka | Deployment / 1 | no PVC in manifest | `apache/kafka:4.3.1` | single broker/controller; no HA |
| Ingress | Traefik Ingress | n/a | n/a | host routing only; TLS external |

Current `k8s/README.md` is stale where it describes three backend replicas and an older backend memory limit.

## Resources

Current manifests request/limit approximately:

- backend: 1 CPU / 1 Gi request, 2 CPU / 2 Gi limit;
- frontend: 50m / 64 Mi request, 250m / 128 Mi limit;
- PostgreSQL: 250m / 256 Mi request, 1 CPU / 500 Mi limit;
- Redis: 100m / 128 Mi request, 500m / 500 Mi limit;
- Kafka: 500m / 512 Mi request, 2 CPU / 1 Gi limit.

These are repository configuration values, not validated production capacity numbers.

## Local deployment helper

`unifydesk-app/k8s.sh`:

- builds backend/frontend images locally with `latest` tags;
- imports images directly into k3s;
- creates a Kubernetes Secret from `.env`;
- applies dependency/backend/frontend manifests;
- supports restart/log/describe/top operations;
- has a destructive `clear-db` path guarded by typing `DELETE`.

This is useful for development/self-hosted testing but is not a production release pipeline.

## Durability assessment

### PostgreSQL

A PVC exists, but the database is a single Deployment. Production still needs managed/HA design or a tested equivalent, automated backups, point-in-time/retention policy, restore validation and RTO/RPO.

### Redis

AOF is enabled, but the K8s manifest has no persistent volume. Pod replacement can therefore lose Redis state. Redis is also single-instance. Production design must decide which Redis state is reconstructable versus durability/security sensitive and provide topology/backup/failover accordingly.

### Kafka

The K8s broker is single-node with no persistent volume. Application producer configuration enables idempotence and `acks=all`, but that does not create durability when the only broker is ephemeral. Production needs persistent broker storage and an appropriate replicated topology/min-ISR policy, or a managed Kafka service.

## Health and rollout

Spring Actuator exposes health and enables health probes, including Prometheus/metrics endpoints. Kubernetes currently probes backend/frontend TCP ports instead of application readiness/liveness endpoints.

Frontend and dependency Deployments use `Recreate`; the backend is one StatefulSet replica. There is therefore no repository-defined high-availability/zero-downtime guarantee.

## Secrets and networking

Runtime secrets are loaded from a generic `messaging-env` Kubernetes Secret created from local `.env`. No external secret manager, rotation workflow, NetworkPolicy or production namespace isolation is defined in these manifests.

CORS/realtime origins default to `https://app.unifydesk.in`. JWT cookies default `secure=false` in application configuration unless deployment overrides it. `APP_SECURITY_TRUST_FORWARDED_HEADERS` defaults false. Production must explicitly validate cookie security, TLS and proxy/client-IP behavior.

## Production target

Before beta/production, create a dedicated infrastructure round to define:

- immutable CI-built images;
- staging/prod environments;
- durable/HA PostgreSQL, Redis and Kafka;
- backup/restore and DR;
- TLS/certificate ownership;
- secret management/rotation;
- actuator probes and controlled rolling deployment;
- monitoring/alerts/log shipping;
- capacity/load baselines;
- rollback to a known image + database compatibility point.
