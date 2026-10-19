package com.messaging.broadcast.msg91;

/**
 * MSG91 Email account-level configuration.
 *
 * <p>The legacy template fields are retained for backwards JSON compatibility only. New connections
 * store only the sending domain; provider template IDs are version-specific and live in
 * email_template_provider_bindings.
 */
public record Msg91EmailProviderConfig(
    String domain, String templateId, String messageVariable, String subjectVariable) {}
