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

SES, SendGrid and Azure Email send both text and HTML when HTML is present. Provider integrations
whose contract is provider-template based can continue using their provider-specific text/template
mapping until they gain an explicit HTML contract.

## Complexity

Compilation, sanitization and variable extraction occur when a template version is saved, not once
per recipient. Delivery rendering is linear in the final message size and replacement values, which
is the minimum work required to construct the outbound message.
