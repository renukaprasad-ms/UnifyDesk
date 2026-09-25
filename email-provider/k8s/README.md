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

The main Messaging Kubernetes stack is not touched by these manifests or by `email-k8s.sh`.

## Why Postfix stays on the host

Postfix owns the EC2 node's sender identity and queue. The Elastic IP, PTR/rDNS, EHLO hostname, outbound TCP/25 and IP reputation are tied to this node. The email-provider pod uses `hostNetwork: true`, allowing it to submit mail to host Postfix at `127.0.0.1:25`.

Do not configure Postfix as an open relay and do not open inbound port 25 merely for outbound sending.

## First setup

```bash
cd email-provider
cp .env.example .env

# Edit all CHANGE_ME values.
nano .env

chmod +x email-k8s.sh
./email-k8s.sh start
```

Generate secrets with:

```bash
openssl rand -base64 32
openssl rand -hex 32
```

## Commands

```bash
./email-k8s.sh start
./email-k8s.sh stop

./email-k8s.sh restart
./email-k8s.sh restart app
./email-k8s.sh restart postgres
./email-k8s.sh restart redis
./email-k8s.sh restart kafka

./email-k8s.sh logs app
./email-k8s.sh logs postgres 200
./email-k8s.sh logs redis
./email-k8s.sh logs kafka

./email-k8s.sh describe app
./email-k8s.sh status
./email-k8s.sh top
./email-k8s.sh top 5
./email-k8s.sh top 1 once

./email-k8s.sh build
./email-k8s.sh apply-env

./email-k8s.sh clear-db
./email-k8s.sh delete
```

`clear-db` and `delete` require explicit confirmation.

`delete` removes workloads but intentionally leaves PVCs in place so database/queue data is not silently destroyed.

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

This is suitable for controlled development/testing, not unrestricted high-volume sending. Monitor `./email-k8s.sh top`; move to a 4-GiB+ instance before sustained traffic.

## SMTP validation

Keep:

```text
EMAIL_PROVIDER_VALIDATION_SMTP_ENABLED=false
```

until a dedicated validator IP/node exists. Recipient probing must not use the production sending IP.
