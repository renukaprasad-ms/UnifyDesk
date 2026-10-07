package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAttachmentInfo;
import java.time.Instant;
import java.util.List;

public record ZohoMailMessageSnapshot(
    String messageId,
    String threadId,
    String folderId,
    EmailFolder folder,
    String internetMessageId,
    String inReplyTo,
    String references,
    String fromAddress,
    String fromDisplayName,
    String replyToAddress,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    String summary,
    String bodyHtml,
    Instant sentAt,
    boolean read,
    List<ZohoAttachmentInfo> attachments) {}
