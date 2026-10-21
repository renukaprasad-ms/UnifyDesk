# UnifyDesk Mail R5 — safe plain-text reading

**Status: draft only, stacked on R4. Not tested or production-ready.**

## Delivered

- The selected email now has a read-only plain-text message preview inside the hosted-mail console.
- The API `GET /console/v1/mail/mailboxes/{mailboxId}/messages/{messageId}/preview` requires the existing console session. Its SQL joins organization, domain, active mailbox, exact console-user mailbox ACL, inbox row, and `READY` intake job before reading any MIME bytes.
- The service verifies the local object's entire SHA-256 digest and byte count against the original durable inbound job before decoding text.
- A streaming Apache James Mime4j **core 0.8.15** parser extracts only `text/plain` parts. RFC 2045 transfer encoding and charset decoding are handled by the parser. Java object memory for text content is capped at 65,536 characters, with bounded parser part and nesting counts; raw messages retain the existing 10 MiB intake cap.
- The parser ignores HTML, `message/rfc822` recursion, attached text documents, and nested parts inherited from attached multipart containers. If only HTML is available, return no preview. Attachment files are never rendered inline.
- The React UI uses plain JSX text rendering in a `pre`, **not** `dangerouslySetInnerHTML`, iframe or browser HTML document. Requests are canceled logically when mailbox or selected message changes; responses are marked `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.
- Existing attachment-free download of the original `.eml` remains possible for trusted local mail clients.

## Cost and efficiency

- No additional cloud services, databases, containers or paid APIs.
- One small streaming MIME parser library (Apache 2.0 license). MIME object stays on existing persistent disk and is not copied to PostgreSQL.
- SHA-256 verification performs O(n) I/O over the stored message using one 8 KiB buffer, then parser reads the MIME source again. O(n) total time with two sequential disk reads is accepted to make **integrity validation mandatory** before preview.
- Extracted body memory is O(min(n, 65,536 characters)), plus bounded parser state. No binary attachment extraction or HTML sanitizer runs.
- All lookups are keyed by the exact organization, user ACL, mailbox and message ID; no global mailbox scans.

## Security decisions

- This does not provide production-grade HTML rendering, attachment preview, unsafe links, remote images or email scripts.
- Treat MIME subject, From address and all content as untrusted. React handles the text as escaped content.
- No general API token may read mailbox content. Organization admins do not inherit permission to read employees' private mail.
- Mailbox access should eventually support explicit delegation, revocation and audit events as part of the planned IAM phase.
- Inbound feature flags and external MX remain **disabled**. The existing UnifyDesk SES provider remains the production sending path.

## QA requirements

1. JDK 25 / Spring Boot 4.1.1 test and Spotless build plus Mime4j dependency resolution, and both Vite feature-flag build variants.
2. End-to-end MIME fixture tests for `multipart/alternative`, quoted-printable UTF-8, base64, attachments, HTML-only, deeply nested MIME, corrupted MIME, oversized decoded plain text, and malformed charsets.
3. Cross-tenant user-ID and mailbox/message-ID authorization, revoked grants, suspended domain/organization, failed/queued scanner jobs, and MIME digest mismatch.
4. Memory profiling under 10 MiB inbound files containing many nested MIME parts and large attachments. Confirm no unbounded allocations or filesystem reads.
5. Browser/XSS tests for HTML injection and remote-image URLs. Confirm no HTML interpretation and no iframe/script execution.
6. Full scanner, PostgreSQL migration, persistent disk, access-control, recovery and monitoring QA from R1–R4 remains required.

## Next functional slice

Secure mail composing and submission for a selected verified mailbox, without giving marketing-sending credentials direct authority over personal mail. Draft/Sent records and idempotent queue handoff must precede a sending UI. Public SMTP/MX gateway, attachment handling, object retention and IAM parity remain required before GA.

**This code has not been built/tested in the current GitHub-only session, and no PR in R1–R5 has been merged into dev.**
