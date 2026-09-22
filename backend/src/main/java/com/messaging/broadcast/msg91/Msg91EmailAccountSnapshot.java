package com.messaging.broadcast.msg91;

public record Msg91EmailAccountSnapshot(
    boolean sendingEnabled, Double maxSendRate, String verifiedIdentity) {}
