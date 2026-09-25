package com.messaging.channel.email;

/**
 * Provider-neutral projection used to update remote email read state without coupling the channel
 * module to inbox persistence entities.
 */
public record EmailReadStateMessage(String externalMessageId, String providerFolderKey) {}
