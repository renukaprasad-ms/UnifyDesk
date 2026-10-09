# UnifyDesk Email — Standalone API Product

**UnifyDesk Email** is a separately operated email-sending product under the UnifyDesk
brand, with its own customer organizations, projects, sending domains, API keys,
delivery queues and infrastructure. It is **not** a subordinate microservice of
the UnifyDesk App.

The UnifyDesk App will be one API customer, using a project-specific key to submit
permitted bulk email. Customers and external developers can use the Email product
without having an account in UnifyDesk App. The product is currently in R1 development;
customer self-service, billing, web console, IP warm-up and live deliverability QA
are still outstanding. This is **not** generally available.

See [product boundary](docs/PRODUCT_BOUNDARY.md) and
[working API contracts](docs/PRODUCT_API_R1.md) for current implementation and release gates.

## Initial architecture

```text
Any authorized customer or UnifyDesk App backend
        |
        | HTTPS /v1 endpoints + project API key
        v
UnifyDesk Email product API (own identity, projects, key auth)
        |
        +-- verified sending domains / status / events / reputation
        +-- PostgreSQL (own product and mail records)
        +-- Redis (quotas / throttling)
        +-- Kafka transactional outbox and async delivery
        +-- DKIM/MIME and Postfix delivery workers
        +-- feedback and recipient suppression
        |
        v
Recipient MX servers
(Gmail / Outlook / Yahoo / others)
```

## Planned stack

- Java 25
- Spring Boot 4.1.x
- Spring Kafka
- PostgreSQL
- Redis
- Postfix as the outbound MTA
- Jakarta Mail / MIME tooling
- DKIM signing with per-domain keys
- Kafka for durable delivery commands and events
- Prometheus + Micrometer metrics
- Grafana dashboards
- Docker
- Kubernetes
- Linux mail nodes with static public IPv4, PTR/rDNS control, and outbound TCP/25
- TLS via publicly trusted certificates

## Responsibilities

- Domain ownership verification
- DKIM key generation and rotation
- SPF / custom MAIL FROM guidance
- Message acceptance and validation
- MIME construction
- Queueing
- Recipient-domain routing
- Rate limiting and throttling
- SMTP delivery
- Retry/deferred delivery
- Bounce classification
- Complaint processing
- Suppression lists
- Unsubscribe enforcement
- IP pools
- IP warm-up
- Tenant quotas
- Reputation tracking
- Delivery events and customer webhooks
- Provider analytics

## Important boundary

UnifyDesk App and other API clients must not directly open SMTP connections to destination MX servers. Outbound delivery must run in dedicated mail nodes/workers so mail reputation, IP pools, port 25 access, retry queues, and scaling are isolated from the SaaS API workload.

## First milestone

V1 will target controlled testing only:

1. One verified sending domain.
2. One static IPv4 mail node.
3. Correct A/PTR/EHLO alignment.
4. Postfix outbound SMTP.
5. STARTTLS.
6. DKIM signing.
7. SPF.
8. DMARC-compatible alignment.
9. Kafka-backed send queue.
10. Hard/soft bounce classification.
11. Suppression list.
12. Delivery events and metrics.

No unrestricted public/customer sending should be enabled until abuse controls, quotas, warm-up, and reputation monitoring are in place.


## Email validation pipeline

Bulk validation is asynchronous and isolated from the outbound delivery path.

```text
API
 |
 v
validation job -> validation batches (default 100 addresses)
 |
 v
transactional outbox
 |
 v
Kafka: email.validation.batch
 |
 v
validator workers
 |-- syntax / normalization
 |-- cached domain + MX routing
 |-- disposable / role checks
 |-- optional SMTP mailbox probe
 |-- optional catch-all detection
 v
persisted item results + atomic job counters
```

Design constraints:

- The request API is bounded to 10,000 addresses so one HTTP request cannot create unbounded heap pressure.
- Each worker keeps only one batch and one per-batch domain map in memory, giving O(batch-size) working memory.
- Job deduplication uses SHA-256 keys and a database uniqueness constraint.
- Domain routing is resolved once per domain per batch and persisted in a TTL cache.
- SMTP validation is disabled by default and must run from validator infrastructure, never from the production sending IP.
- Redis provides a shared per-domain probe budget while local semaphores cap same-node concurrency.
- SMTP temporary failures, rate limits and anti-enumeration responses are classified UNKNOWN, not INVALID.
- Catch-all probing uses randomized recipient addresses and is cached at domain level.
- Batch claims and job counters use pessimistic locking so duplicate Kafka delivery cannot double-count results.
- Terminal batch failures are surfaced as UNKNOWN and jobs finish as PARTIAL_FAILED or FAILED rather than hanging indefinitely.

Endpoints:

- `POST /api/v1/companies/{companyId}/email-validation/jobs`
- `POST /api/v1/companies/{companyId}/email-validation/single`
- `GET /api/v1/companies/{companyId}/email-validation/jobs/{jobId}`
- `GET /api/v1/companies/{companyId}/email-validation/jobs/{jobId}/results`

For very large CSV/contact imports, consumers should stream rows into bounded validation jobs rather than loading the complete file into memory.

For public project-key API routes, see `/v1/` in [PRODUCT_API_R1.md](docs/PRODUCT_API_R1.md). The legacy `/api/v1/companies/` routes remain private operational APIs and must never be exposed directly to customers.

## Dedicated Kubernetes deployment

Use the independent email-provider script on your **dedicated K3s mail node**:

```bash
cd email-provider
cp .env.example .env
# Edit .env to configure secrets and runtime addresses
./k8s.sh validate
./k8s.sh start
./k8s.sh status
./k8s.sh logs app
./k8s.sh port-forward
```

Open `http://127.0.0.1:18010/console/index.html` from the same host after port forwarding. `./email-k8s.sh` remains supported as an alias; see [K8s deployment instructions](k8s/README.md) for operating commands, Postgres-18 migration warnings and live sending safeguards.
