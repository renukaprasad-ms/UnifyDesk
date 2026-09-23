package com.messaging.broadcast.dto;

public record BroadcastMemberImportResponse(
    long processed,
    long insertedOrReactivated,
    long duplicates,
    long invalid) {}
