package com.messaging.integrations.unifydeskemail;

/** Stored only as encrypted JSON in the existing Broadcast sender connection. */
public record UnifyDeskEmailStoredCredentials(String apiKey, Long sendingDomainId) {}
