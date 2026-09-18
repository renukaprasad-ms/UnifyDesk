package com.messaging.inbox.dto;

public record InboxAttachmentResponse(
    String id,
    String externalAttachmentId,
    String filename,
    String mimeType,
    Long sizeBytes) {}
