package com.messaging.inbox.dto;

import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import java.time.Instant;
import java.util.List;

public record InboxMessageResponse(
    String id,
    String conversationId,
    String externalMessageId,
    String internetMessageId,
    String providerFolderKey,
    InboxMessageDirection direction,
    InboxMessageStatus status,
    String subject,
    String snippet,
    String bodyText,
    String bodyHtml,
    String fromAddress,
    String replyToAddress,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String inReplyTo,
    String references,
    Instant sentAt,
    boolean read,
    String whatsappStatus,
    List<InboxAttachmentResponse> attachments) {}
