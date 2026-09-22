package com.messaging.broadcast.sendgrid;

public record SendGridAccountSnapshot(
    boolean sendingEnabled, Double maxSendRate, String verifiedIdentity) {}
