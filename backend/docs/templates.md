# Templates

Messaging uses one company-scoped Template Library for Email, WhatsApp and SMS.

## User flow

1. Choose the channel.
2. Choose the template purpose/category.
3. Build the content.
4. Add personalization variables.
5. Preview with sample recipient values.
6. Save/activate, or submit to a provider when provider approval is required.
7. Select the active template from Broadcast.

The Templates page also exposes a **How it works** guide describing this flow in product language.

## Email templates

Email uses a structured visual design rather than asking users to maintain raw HTML.

The current design model supports:

- brand/background colors
- content width
- header logo
- header heading
- ordered Heading/Text/Image/Button/Divider/Spacer blocks
- footer company name/address/privacy link
- unsubscribe footer setting
- desktop/mobile preview
- recipient-variable preview values

The structured design is stored in `message_templates.design_json`. The template also stores generated
`body_text` and `body_html` snapshots so delivery code does not need to rebuild the editor model.

Images are represented in the design by media IDs. Signed preview URLs are never persisted in the
template.

## Media and S3

Template logos and images use the existing Media subsystem with `MediaPurpose.TEMPLATE_MEDIA`.
Company logos are also visible in the media picker.

The upload flow is:

1. initiate media upload
2. receive a signed storage upload URL
3. upload directly from the browser to storage
4. complete/validate the media object
5. store the returned media ID in the template design

The storage abstraction supports Supabase and S3. S3 uses:

- `MEDIA_STORAGE_DEFAULT_PROVIDER=S3`
- `S3_MEDIA_REGION`
- `S3_MEDIA_BUCKET`
- optional S3 endpoint and explicit credentials
- the AWS default credential chain when explicit credentials are omitted

Company media is shared with authorized company users through the Media API.

## Variables

Variables use double braces:

```
{{first_name}}
{{last_name}}
{{full_name}}
{{email}}
{{phone}}
{{destination}}
{{current_date}}
{{current_year}}
{{discount}}
{{offer_url}}
```

Built-in values come from the recipient. CSV/custom fields are stored in group-member metadata and
are snapshotted into Broadcast recipients when a run starts.

Delivery resolves variables per recipient. A recipient with a required unresolved variable is failed
with `MISSING_TEMPLATE_VARIABLE` instead of receiving a raw `{{variable}}` token.

## WhatsApp

WhatsApp template content remains provider-aware. A local template is not considered sendable until
Meta approval has been synchronized. Local lifecycle and provider lifecycle are intentionally
separate.

## SMS / MSG91

SMS templates store reusable local message content. MSG91 Flow/DLT/provider-template mapping remains
a provider mapping concern and should not be hard-coded into the generic template entity.

## Delivery note

The visual Email builder persists both generated text and generated HTML. The current Broadcast
transport path still sends the text snapshot. Rich HTML delivery and stable delivery-time resolution
of template media IDs must be wired through the email provider transports before branded Email
templates should be considered fully production-ready.
