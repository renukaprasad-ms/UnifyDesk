package com.messaging.emailtemplate.dto;

import java.util.List;

public record EmailTemplatePreviewResponse(
    String subject,
    String preheader,
    String html,
    String text,
    List<String> variables,
    List<String> unresolvedVariables) {}
