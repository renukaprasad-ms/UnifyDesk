package com.messaging.emailprovider.validation.service;

public record ValidationBatchStats(
    long processed, long valid, long invalid, long risky, long unknown) {}
