# UnifyDesk Website

Public marketing site for UnifyDesk, built with Next.js and TypeScript.

## Local development

From this directory:

```bash
npm install
npm run dev
```

## Production build

```bash
npm run build
```

## Netlify

When connecting the monorepo, use the repository root and keep `netlify.toml` at `unifydesk-website/netlify.toml`, or configure Netlify's package directory as `unifydesk-website`. The public production domain is `unifydesk.in`.

The application itself lives at `app.unifydesk.in`; this site must never contain backend/provider secrets.
