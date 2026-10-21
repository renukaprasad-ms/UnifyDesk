# UnifyDesk Monitor V1

Lightweight request-event ingestion and aggregated statistics using Go 1.27.3,
net/http, database/sql, pure Go modernc.org/sqlite, and JSON slog logging.
No Spring Boot integration or external database server is included.

## Architecture

`cmd/server` owns configuration, database lifetime, HTTP timeouts, retention and
signal handling. `internal/handler` decodes HTTP; `internal/service` validates
and runs retention; `internal/repository` performs parameterized SQL and transactions.
`internal/model`, `internal/middleware`, `internal/config`, and embedded
`migrations/001_init.sql` contain shared types, HTTP security, configuration and schema.
Schema initialization is transactional and idempotent; it never drops existing data.
Future schema changes require explicit versioned migration planning.

## Run with Docker (PowerShell)

```powershell
Copy-Item .env.example .env
# Edit .env and replace MONITOR_API_KEY with a strong random secret.
docker compose up --build -d
docker compose logs monitor
docker compose ps
```

The existing `docker-compose.yaml` is the canonical Compose file. Port 8080 is
bound only to host loopback. SQLite persists in `./data`, mounted at
`/var/lib/unifydesk-monitor`. The image runs as UID/GID 10001; on Linux prepare
that directory with `mkdir -p data` and `sudo chown 10001:10001 data` before starting.
Do not use world-writable database permissions. Docker Desktop handles host mounts.

| Variable | Default / purpose |
|---|---|
| PORT | 8080; Compose fixes the internal port at 8080 |
| DATABASE_PATH | ./data/monitor.db natively; Compose uses /var/lib/unifydesk-monitor/monitor.db |
| MONITOR_API_KEY | Required; startup fails if absent |
| EVENT_RETENTION_DAYS | 7; positive integer, maximum 3650 |
| CLEANUP_INTERVAL | 1h; Go duration, minimum 1s |
| MAX_BODY_BYTES | 1048576; 1024 to 16777216 bytes, both ingestion endpoints |
| LOG_LEVEL | info; debug, info, warn, error |
| GOMEMLIMIT | Compose sets 96MiB, a soft Go memory limit |

Compose supplies its database path and port explicitly. For native configuration,
all listed environment variables are read directly. No .env loader is used by Go.

## HTTP API

All responses are JSON. Errors use `{"error":{"message":"..."}}`.
Unknown routes return 404; wrong methods return 405. Monitoring routes require
`Authorization: Bearer <MONITOR_API_KEY>`; no tokens or request payloads are logged.

| Method | Route | Result |
|---|---|---|
| GET | /health | 200: {"status":"UP","service":"unifydesk-monitor"} |
| GET | /ready | 200: {"status":"UP"}; SQLite unavailable: 503 |
| POST | /api/v1/events | 201: {"inserted":1}, only after commit |
| POST | /api/v1/events/batch | 201: {"inserted":N}, single atomic transaction |
| GET | /api/v1/stats | 200: aggregated statistics |

Single event:
```json
{"service":"unifydesk-api","method":"GET","path":"/api/v1/contacts","status":200,"latency_ms":45,"client_ip":"203.0.113.10"}
```
Batch: `{"events":[<event>,<event>]}`, between 1 and 500 entries. Every entry is
validated before any insert. Service is 1-128 bytes, path starts with `/` and is
at most 2048 bytes, method is a standard uppercase HTTP method, status is 100-599,
and latency_ms is a required nonnegative integer. Optional client_ip must be a
plain IPv4/IPv6 address without a zone. Control characters are rejected in service
and path. Unknown fields, trailing JSON, missing required fields, malformed JSON,
and invalid batches return 400; oversized bodies return 413. Database failures
return 503 with Retry-After: 1. Retrying ingestion can duplicate records; V1 has no
idempotency keys. Upstreams should retry carefully with bounded backoff.

Stats accepts optional `service`, `from`, `to` (RFC3339 timestamps with offsets).
The interval includes from and excludes to. Defaults are now minus 24 hours to
now; supplying only to gives its preceding 24 hours; only from uses now as to.
Maximum range is 31 days. Invalid/duplicate/unknown parameters return 400.
```json
{"service":"unifydesk-api","total_requests":3,"successful_requests":1,"client_error_requests":1,"server_error_requests":1,"average_latency_ms":45,"error_rate_percent":66.66666666666666}
```
2xx/3xx count as successful; 4xx and 5xx count as errors. 1xx count in totals and
average latency only. Empty results use numeric zeros. Omitted service aggregates
all services and returns an empty service string. Aggregation runs in SQL.

```powershell
$key = (Get-Content .env | Where-Object { $_ -like 'MONITOR_API_KEY=*' }) -replace '^MONITOR_API_KEY=', ''
$headers = @{ Authorization = "Bearer $key" }
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/ready
$event = @{service='unifydesk-api';method='GET';path='/api/v1/contacts';status=200;latency_ms=45}
Invoke-RestMethod http://localhost:8080/api/v1/events -Method Post -Headers $headers -ContentType 'application/json' -Body ($event | ConvertTo-Json)
Invoke-RestMethod http://localhost:8080/api/v1/events/batch -Method Post -Headers $headers -ContentType 'application/json' -Body (@{events=@($event,$event)} | ConvertTo-Json -Depth 4)
Invoke-RestMethod http://localhost:8080/api/v1/stats?service=unifydesk-api -Headers $headers
```

## Tests and builds

No host Go installation is required:
```powershell
docker run --rm -v "${PWD}:/src" -w /src golang:1.27-alpine sh -c 'go test ./... && go vet ./...'
docker build -t unifydesk-monitor:v1 .
docker buildx build --platform linux/arm64 -t unifydesk-monitor:arm64 --load .
docker stats --no-stream $(docker compose ps -q monitor)
docker compose stop
```
Tests use temporary real SQLite databases, including atomic rollback, retention,
reopening persisted data, authentication, HTTP validation, database errors and
worker cancellation. Container stop exercises signal-driven HTTP draining.
The runtime contains no compiler, uses CGO_ENABLED=0 and supports native ARM64
builds. Record actual memory under representative load; neither the 128MiB
container cap nor GOMEMLIMIT proves a workload memory target. Body limits and
500-event batches bound per-request allocation; there is no unbounded queue.
SQLite uses one pooled connection, WAL, a five-second busy timeout and foreign
keys on each connection. HTTP has read/header/write/idle timeouts and 16KiB headers.

## Storage, retention, backups and operations

`request_events`: id INTEGER PRIMARY KEY; service, method, path TEXT NOT NULL;
status, latency_ms INTEGER NOT NULL with range checks; client_ip nullable TEXT;
created_at TEXT NOT NULL. Server timestamps are UTC RFC3339 with fixed nine-digit
fractional seconds, preserving SQL lexical time ordering. Indexes cover created_at,
(service, created_at), and (status, created_at).

Retention runs at startup and every configured interval, removing expired records
in transactions of at most 1000 rows, at most 100 batches per cycle. Cleanup has a
30-second budget; a backlog takes multiple cycles. Deleted pages are reused rather
than returned to the filesystem, so database size reflects its high-water mark.
SQLite's normal automatic WAL checkpointing remains enabled. Long readers can
prevent WAL truncation: monitor disk space and investigate persistent WAL growth.
Schedule a maintenance `PRAGMA wal_checkpoint(TRUNCATE)` when quiet. VACUUM needs
extra disk space and an exclusive maintenance window; it is never run automatically.
Retention is not a hard disk-size quota; capacity-plan for incoming traffic.

For reliable backups, stop the service cleanly, then copy the data directory,
or use SQLite's online `.backup` command from an installed sqlite3 CLI while the
service runs. Do not copy only monitor.db from a live WAL database. Preserve
ownership and test restores. Inspect with `sqlite3 data/monitor.db` on a host with
the CLI installed, then `.schema request_events` and `SELECT COUNT(*) FROM request_events;`.
The minimal runtime intentionally omits this administrative CLI. Use read-only
inspection where possible and avoid long-running queries on the production file.

On a small AWS EC2 ARM64 machine, build for linux/arm64, persist on local EBS,
keep the service private via security groups, and use a trusted TLS reverse proxy
or private tunnel for remote clients. SQLite WAL requires local filesystem locking;
do not share a database across replicas or put it on NFS. One service instance is
the V1 deployment model. Protect the key with deployment secrets, restrict data
permissions, monitor disk usage, back up EBS/SQLite and test graceful restarts.
V1 has no rate limiter, per-tenant authorization, deduplication, rollups or metrics
exporter. A trusted proxy can limit connection concurrency and request rate.

Future Spring Boot producers can use a bounded in-memory buffer and a small
scheduled worker to send up to 500 events per batch asynchronously. Define overflow
and retry policies so business requests never block indefinitely. No integration
is implemented here. The producer supplies client_ip only after its own trusted
proxy resolution; this service never derives it from sender addresses or forwarded
headers. Do not send secrets, bodies, cookies, tokens or sensitive query strings in
paths. Client IP is personal data; omit it when unnecessary and apply retention.

## Verification recorded for this implementation

On 20 October 2026, Docker-based `go test ./...` and `go vet ./...` passed.
The multi-stage production image built and Compose started it successfully.
Real HTTP requests returned health/readiness 200, authenticated event insertion
201, and statistics containing the committed event. Docker health was healthy.
A single post-smoke-test `docker stats --no-stream` sample reported 4.766MiB
against the 128MiB cap on Docker Desktop amd64. This is an idle/small-workload
observation, not a sustained-load benchmark or an ARM64 memory measurement.
SIGTERM via `docker stop` exited with code 0 and OOMKilled=false. The verification
container was left stopped; its smoke-test database is ignored under data/.
Linux ARM64 cross-compilation also passed with `CGO_ENABLED=0 GOOS=linux
GOARCH=arm64 go build`; execution on an ARM64 EC2 machine remains to be validated.
