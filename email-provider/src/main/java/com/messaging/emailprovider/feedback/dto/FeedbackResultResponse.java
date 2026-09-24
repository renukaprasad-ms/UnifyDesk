package com.messaging.emailprovider.feedback.dto;

import com.messaging.emailprovider.feedback.EmailEventType;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;

public record FeedbackResultResponse(
    boolean duplicate,
    String messageId,
    String recipientId,
    EmailEventType eventType,
    EmailRecipientStatus recipientStatus) {}
