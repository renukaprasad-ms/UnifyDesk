# Local and Cloudflare Tunnel development

Prerequisites: Docker Desktop, Node.js 24, Java 25. Run commands from this directory.

## Run the whole app in Docker

Local mode:

```powershell
.\local.ps1 up local
```

Open http://localhost:5100. API requests use http://localhost:8001.

Tunnel mode:

```powershell
.\local.ps1 up tunnel
```

Start your Cloudflare tunnel separately, then open https://app.unifydesk.in. API requests use https://api.unifydesk.in. Tunnel routes:

| Public hostname | Local service |
| --- | --- |
| app.unifydesk.in | http://localhost:5100 |
| api.unifydesk.in | http://localhost:8001 |

The two modes share ports, Docker containers, database data, and signing keys. Run one mode at a time. Running `up` for the other mode rebuilds the frontend with the chosen API URL and recreates containers as needed.

Stop without deleting data:

```powershell
.\local.ps1 down local
# Or, for tunnel mode:
.\local.ps1 down tunnel
```

Equivalent direct Compose commands:

```powershell
docker compose --env-file .env.local -f docker-compose.yml -f compose.local.yaml up --build -d
docker compose --env-file .env.tunnel -f docker-compose.yml -f compose.tunnel.yaml up --build -d
```

## Run backend/frontend natively

For local mode, start dependencies and backend in one terminal:

```powershell
.\local.ps1 infra local
.\local.ps1 backend local
```

In another terminal:

```powershell
.\local.ps1 frontend local
```

For tunnel mode, replace `local` with `tunnel` in all three commands. Do not also run the Docker backend/frontend on the same ports. Stop native processes with Ctrl+C.

## Environment files

| Mode | Backend / Compose variables | Native Vite variables |
| --- | --- | --- |
| Local | .env.local | frontend/.env.local |
| Tunnel | .env.tunnel | frontend/.env.tunnel |

The launcher defaults to local mode. It explicitly selects the env file for Compose and the Vite mode for native development. The original `.env` is retained but is not used by these launcher commands.

Local mode uses localhost for frontend/API origins, OAuth callbacks, webhooks, and payment return URLs, with Secure cookies disabled. Tunnel mode uses the public HTTPS domains with Secure cookies enabled. Each provider dashboard must use the callback URLs matching the selected mode; providers requiring public callbacks need tunnel mode.

The admin email is `superadmin@localhost.test`. Read `BOOTSTRAP_SUPER_ADMIN_PASSWORD` from the selected backend env file. All four environment files are excluded from Git. Existing database credentials and signing keys are preserved in both modes. If you change provider credentials, update each mode you intend to use. Never put provider secrets in frontend env files.

PostgreSQL listens at localhost:5001, Redis at localhost:6001, Kafka at localhost:9001. Data stays in Docker volumes. External email, storage, OAuth, payment, and messaging providers need their own development credentials.

### Backend memory and appearance defaults

The shared local/tunnel Compose backend now defaults to a 1536 MB container, a 512 MB Java heap, and a 512 MB Metaspace cap. This leaves room for class metadata and other native allocations. The JVM exits on an out-of-memory error instead of continuing in an unreliable state. Override with `BACKEND_MEMORY_LIMIT` and `BACKEND_JAVA_TOOL_OPTIONS` when needed; changing heap alone does not change Metaspace.

Recreate the backend to apply changed JVM settings. CSV preview failures now stop the loading placeholder and offer **Retry preview** for the same upload.

Personal appearance supports **Device**, **Light**, and **Dark**. Device is the default for accounts without an explicit choice and follows system changes live. Migration V115 updates the database default to SYSTEM and preserves existing explicit Light/Dark preferences.
