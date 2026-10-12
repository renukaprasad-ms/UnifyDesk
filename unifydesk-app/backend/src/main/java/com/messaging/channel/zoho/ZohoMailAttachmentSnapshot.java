package com.messaging.channel.zoho;

public record ZohoMailAttachmentSnapshot(
    String attachmentId, String filename, Long sizeBytes, boolean inline) {}
