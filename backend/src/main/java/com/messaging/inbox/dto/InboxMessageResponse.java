package com.messaging.inbox.dto;

import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import java.time.Instant;
import java.util.List;

public record InboxMessageResponse(
    String id,
    String externalMessageId,
    String internetMessageId,
    InboxMessageDirection direction,
    InboxMessageStatus status,
    String subject,
    String snippet,
    String bodyText,
    String bodyHtml,
    Instant sentAt,
    boolean read,
    List<InboxAttachmentResponse> attachments) {}
