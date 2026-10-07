package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import java.time.Instant;
import java.util.List;

public record ZohoMailMessageSnapshot(
    String messageId,
    String mailId,
    String threadId,
    String folderId,
    String fromAddress,
    String fromDisplayName,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    String summary,
    String bodyHtml,
    Instant sentAt,
    boolean read,
    EmailFolder folder,
    List<ZohoMailAttachmentSnapshot> attachments) {}
