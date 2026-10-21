# Isolated Postfix bounce pipe sample (R9)

This directory contains **staging-only examples**, not enabled production
configuration. Never route an unrelated mail domain through this pipe.

1. Configure a dedicated bounce subdomain (e.g. `bounces.example.net`)
   with DNS MX pointing to a mail ingress node you control. Configure SMTP
   restrictions (no open relay), per-IP limits, and safe rejection of
   unknown/invalid bounce recipient local parts before accepting DATA.
2. In Postfix's `transport_maps`, add a **domain-exact** route:

   ```text
   bounces.example.net   udmail-dsn:
   ```

   Retain existing transport map rules. Rebuild the hash map using `postmap`.
3. In a reviewed `master.cf`, add a dedicated pipe service:

   ```text
   udmail-dsn  unix  -  n  n  -  -  pipe
     flags=Rq user=udmail-dsn argv=/usr/local/bin/udmail-dsn-pipe.sh ${recipient}
   ```

   Create the least-privileged local `udmail-dsn` user. The script must have
   executable permissions but no shell secrets. The private curl config must
   be readable only by this service user, owned appropriately, with mode 0400.
4. Install `feedback-curl.conf.example` only after replacing its placeholders
   in the **private installed copy**. Never commit the actual tokens or mount
   the config into Cloudflare Pages. Configure localhost-only provider backend
   port access; the endpoint is under the existing internal bearer filter.
5. Test one bounce/recipient, invalid HMAC, oversized DSN, MTA retry on HTTP
   failure, and multi-recipient DSNs. Confirm `curl` returns nonzero for any
   error; a failure must not silently acknowledge and discard bounce feedback.
6. Configure the running mailbox worker's matching bounce-domain and HMAC key,
   with a planned key-rotation overlap. Enable no sending until MX, DKIM, local
   relay isolation and bounce recovery tests pass.

The pipe is an **example**, not an activated SMTP integration. The
single-recipient R9 parser intentionally fails closed on ambiguous DSNs.
Also validate the exact `pipe(8)` syntax and command behavior against the
installed Postfix version before rollout.
