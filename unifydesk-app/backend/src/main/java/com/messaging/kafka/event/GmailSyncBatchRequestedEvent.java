package com.messaging.kafka.event;

public record GmailSyncBatchRequestedEvent(Long batchId, Long jobId, Long connectionId) {}
