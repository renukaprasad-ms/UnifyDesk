# UnifyDesk Email frontend

React 19 + Vite. All browser calls go to same-origin `/console/v1/` via Nginx; no CORS or internal operator bearer token is required. Protected project APIs are deliberately not proxied.

Build and develop:

```bash
cd email-provider/frontend
npm install
npm run build
# for hot reload, run ../k8s.sh api-port-forward in another shell:
npm run dev
```

Deploy through root `email-provider/k8s.sh`: `./k8s.sh restart frontend`.
Screens: Overview, Projects, DNS domains, API keys, Webhooks, Usage and Account.
Secrets are shown only once; console sessions live in per-tab session storage.
See root README for operator provisioning and security boundaries.
