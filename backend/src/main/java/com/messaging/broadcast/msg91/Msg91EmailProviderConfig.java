package com.messaging.broadcast.msg91;

public record Msg91EmailProviderConfig(
    String domain, String templateId, String messageVariable, String subjectVariable) {}
