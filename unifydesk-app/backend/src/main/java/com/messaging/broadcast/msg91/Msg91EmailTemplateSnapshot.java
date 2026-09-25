package com.messaging.broadcast.msg91;

import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;

public record Msg91EmailTemplateSnapshot(
    String providerTemplateId,
    String slug,
    String providerStatus,
    EmailTemplateProviderBindingStatus status) {}
