package com.messaging.broadcast.ses;

public record AmazonSesAccountSnapshot(
    boolean productionAccessEnabled,
    boolean sendingEnabled,
    Double max24HourSend,
    Double maxSendRate,
    Double sentLast24Hours,
    String verifiedIdentity) {}
