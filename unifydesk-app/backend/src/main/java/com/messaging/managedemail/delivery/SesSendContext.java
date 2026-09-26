package com.messaging.managedemail.delivery;

import software.amazon.awssdk.services.sesv2.SesV2Client;

public record SesSendContext(
    SesV2Client client,
    boolean managed,
    String tenantName,
    String configurationSetName,
    Long domainId) {}
