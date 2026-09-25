# Email Templates

Templates are Email-only in this phase. SMS and WhatsApp template products remain separate because
their provider rules, rendering and approval lifecycles differ.

## Product flow

Users can:

1. copy one of the platform catalog templates,
2. start a blank structured Email,
3. paste custom HTML.

Catalog items are immutable. "Use template" creates a company-owned copy.

## Versioning

Email template content is immutable by version.

- Save draft creates a new version.
- Publish points the template at the current version.
- Editing a published template creates unpublished changes without mutating the published version.
- Broadcast copies the exact published subject, text and HTML into its run snapshot.

This guarantees that later edits cannot change recipients already queued for delivery.

## Builder

Builder JSON is the source of truth for visual templates. Schema version 1 intentionally supports a
small Email-safe block set:

- HEADER
- HERO
- HEADING
- TEXT
- IMAGE
- BUTTON
- DIVIDER
- SPACER
- FOOTER

The backend compiler generates table-based HTML and a plain-text fallback once when a version is
saved. Per-recipient delivery only resolves variables.

## Custom HTML

HTML mode stays separate from the visual builder. Arbitrary HTML is not converted to blocks.

Server-side sanitization rejects scriptable/interactive content such as scripts, iframes, forms,
event handlers, javascript URLs and CSS expressions. The frontend preview uses a sandboxed iframe.

## Variables

Variables use double braces, for example:

```
{{first_name}}
{{last_name}}
{{full_name}}
{{email}}
{{destination}}
{{current_date}}
{{current_year}}
{{coupon_code}}
{{offer_url}}
```

Variables are extracted once per template version. Broadcast resolves them against recipient fields
and imported metadata. A recipient with unresolved required variables fails with
`MISSING_TEMPLATE_VARIABLE` rather than receiving a raw token.

## Media

Template logos and images reuse the private Media subsystem. Uploads use `TEMPLATE_MEDIA`.

An image becomes Email-public only after it is explicitly registered as an Email template asset.
That produces an opaque stable URL under:

```
/api/v1/email-assets/{opaque-token}
```

The URL redirects to a fresh short-lived signed storage URL each time it is requested. This preserves
private storage while avoiding broken images after an S3/Supabase signature expires.

## Delivery

Email Broadcast stores:

- source template ID
- semantic source version
- subject snapshot
- plain-text snapshot
- HTML snapshot

SES, SendGrid and Azure Email send both text and HTML when HTML is present.

### MSG91 Email

MSG91 Email uses provider-side templates, so the MSG91 connection stores only account/domain
configuration and encrypted credentials. A provider template ID never belongs to the connection.

Templates have an immutable delivery purpose per version:

- `MARKETING` for promotional offers, newsletters and campaigns.
- `TRANSACTIONAL` for expected account/service messages.

Only a published `TRANSACTIONAL` version can be synced to MSG91.

Each sync creates or resolves a row in `email_template_provider_bindings` keyed by:

- template version
- MSG91 sender connection
- provider

The binding stores the provider template ID/slug, content hash, provider status and timestamps.
The deterministic provider slug contains the template ID, semantic version and content hash prefix,
which makes retries idempotent.

The supported flow is:

```
MSG91 account connection
    -> publish transactional Email template
    -> sync exact published version to MSG91
    -> refresh provider status when needed
    -> select the same MSG91 connection in Broadcast
    -> choose only a sendable binding for that exact version
    -> snapshot provider template ID into broadcast_runs
    -> resolve recipient variables
    -> send template_id + variables to MSG91
```

MSG91 Broadcast does not support scratch Email content. This prevents a campaign from passing local
validation while lacking the required provider template.

Disconnecting an MSG91 account disables its sender and therefore prevents new delivery. Binding
history remains attached to the connection until the disconnected connection data is purged. Runs
keep their provider template ID snapshot independently.

Recipient values are validated before provider submission. Missing variables fail the individual
recipient with `MISSING_TEMPLATE_VARIABLE`; unsafe URL schemes or markup-bearing MSG91 variables
fail with `UNSAFE_TEMPLATE_VARIABLE`.

## Complexity

Compilation, sanitization and variable extraction occur when a template version is saved, not once
per recipient. Delivery rendering is linear in the final message size and replacement values, which
is the minimum work required to construct the outbound message.
