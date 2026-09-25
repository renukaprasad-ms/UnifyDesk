package com.messaging.kafka.event;

import com.messaging.channel.email.EmailFolder;

public record InboxMarkAllReadRequestedEvent(
    Long jobId, Long connectionId, EmailFolder folder) {}
