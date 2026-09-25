package com.messaging.broadcast.msg91;

public record Msg91AccountSnapshot(
    boolean sendingEnabled, Double maxSendRate, String verifiedIdentity) {}
