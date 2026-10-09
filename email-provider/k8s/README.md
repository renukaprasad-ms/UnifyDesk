# Email Platform Kubernetes

This directory is the isolated runtime stack for the first-party email platform.

## Runtime boundary

```text
EC2 mail node
├── host OS
│   └── Postfix
└── K3s namespace: email-platform
    ├── email-provider
    ├── email-postgres
    ├── email-redis
    └── email-kafka (optional/local mode)
```

The main UnifyDesk App Kubernetes stack is not touched by these manifests or by `k8s.sh`. The older `email-k8s.sh` is a compatibility wrapper.

## Why Postfix stays on the host

Postfix owns the EC2 node's sender identity and queue. The Elastic IP, PTR/rDNS, EHLO hostname, outbound TCP/25 and IP reputation are tied to this node. The email-provider pod uses `hostNetwork: true`, allowing it to submit mail to host Postfix at `127.0.0.1:25`.

Do not configure Postfix as an open relay and do not open inbound port 25 merely for outbound sending.

## First setup

```bash
cd email-provider
cp .env.example .env

# Edit all CHANGE_ME values.
nano .env

chmod +x k8s.sh
./k8s.sh validate
./k8s.sh start
```

Generate secrets with:

```bash
openssl rand -base64 32
openssl rand -hex 32
```

## Commands

```bash
./k8s.sh start
./k8s.sh stop

./k8s.sh restart
./k8s.sh restart app
./k8s.sh restart postgres
./k8s.sh restart redis
./k8s.sh restart kafka

./k8s.sh logs app
./k8s.sh logs postgres 200
./k8s.sh logs redis
./k8s.sh logs kafka

./k8s.sh describe app
./k8s.sh status
./k8s.sh top
./k8s.sh top 5
./k8s.sh top 1 once

./k8s.sh build
./k8s.sh apply-env
./k8s.sh validate
./k8s.sh port-forward 18010

./k8s.sh clear-db
./k8s.sh delete
```

**`start` restores the workloads after `stop`**, including their persisted volumes. `restart` (all) rebuilds/redeploys only the app, waiting for dependencies; it **does not restart Postgres**. `restart postgres` is the explicit database restart.

`clear-db` and `delete` require explicit confirmation. `clear-db` also refuses to clear data while the email app is running; stop first and then run `clear-db`. It will temporarily start Postgres as needed.

`delete` removes workloads but intentionally leaves PVCs in place so database/queue data is not silently destroyed.

## Security, persistence and console access

The script refuses placeholder DB/Redis/master-key credentials or a missing/short internal API token. Fill in `EMAIL_PROVIDER_INTERNAL_API_TOKEN` (32+ characters), `EMAIL_PROVIDER_MASTER_KEY` (base64 encoding of exactly 32 random bytes), and individual database/Redis credentials. If feedback is enabled, `EMAIL_PROVIDER_FEEDBACK_TOKEN` must also be 32+ characters. **Keep delivery disabled** with `EMAIL_PROVIDER_DELIVERY_ENABLED=false` until live Postfix/IP/DNS and compliance tests are complete. The example uses `FLYWAY_BASELINE_ON_MIGRATE=false` for safer fresh deployment.

Generate secrets safely:

```bash
openssl rand -base64 32                 # master AES-256 key
openssl rand -hex 32                    # internal API, feedback, unsubscribe keys (each unique)
```

PostgreSQL 18's default PGDATA is nested beneath `/var/lib/postgresql`. The manifest mounts the PVC at `/var/lib/postgresql` to ensure database data survives pod replacement. **If you already initialized a cluster with the previous mount, back up and inspect its actual PGDATA before applying this change**. A PVC alone does not mean the original database files were persisted.

The script proactively **blocks an automatic update** when an existing `email-postgres` StatefulSet still mounts `/var/lib/postgresql/data`. Restore or migrate the old cluster's actual PGDATA first, then explicitly apply the new `k8s/postgres.yaml` after the data has been secured. This check is not a substitute for verifying recovery from backup.

The Email product HTTP API is ClusterIP-only. For **local-only** access to the independent console:

```bash
./k8s.sh port-forward
# open http://127.0.0.1:18010/console/index.html
```

Do not expose `/internal/v1/` over a public ingress. Postfix stays on the host, outside Kubernetes; the email pod uses `hostNetwork: true`, so firewall/restrict **host TCP 8010**. K3s must run on the dedicated mail node (Postfix reachable on host loopback). The script never configures a Postfix open relay, routes public email traffic, or opens port 25.

`EMAIL_PROVIDER_KAFKA_MODE=external` does not delete a previously-created local Kafka deployment automatically, avoiding unintended queue loss. Clean up local Kafka separately after migrating data.

## Local smoke check (no Kubernetes changes)

```bash
./tests/k8s-script-smoke.sh
```

The smoke check uses temporary fake `kubectl`, `docker`, and `k3s` binaries to
validate script syntax, configuration security, service scaling and database restart
safety without accessing your cluster. It does **not** test the actual Docker image
build, Postfix, real Postgres persistence, or Kubernetes rollout.

## Kafka mode

The default isolated test deployment uses:

```text
EMAIL_PROVIDER_KAFKA_MODE=local
KAFKA_BOOTSTRAP_SERVERS=email-kafka.email-platform.svc.cluster.local:9092
```

For a larger deployment, switch to:

```text
EMAIL_PROVIDER_KAFKA_MODE=external
KAFKA_BOOTSTRAP_SERVERS=broker-1:9092,broker-2:9092
```

The script then skips/deletes the local Kafka workload.

## t3.small sizing

The manifests are deliberately conservative for a 2-vCPU / 2-GiB test node:

- email-provider: 320 MiB Java heap, 512 MiB pod limit
- Postgres: 96 MiB shared buffers, 320 MiB pod limit
- Redis: 96 MiB max data, 160 MiB pod limit
- Kafka: 256 MiB heap, 512 MiB pod limit

This is suitable for controlled development/testing, not unrestricted high-volume sending. Monitor `./k8s.sh top`; move to a 4-GiB+ instance before sustained traffic.

## SMTP validation

Keep:

```text
EMAIL_PROVIDER_VALIDATION_SMTP_ENABLED=false
```

until a dedicated validator IP/node exists. Recipient probing must not use the production sending IP.
