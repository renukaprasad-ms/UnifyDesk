package com.messaging.ads.meta.dto;

public record MetaCustomerListMembersResponse(
    String audienceId,
    MetaCustomerListMembersRequest.Operation operation,
    int submittedRows,
    int batchCount,
    int providerReceivedRows,
    int providerInvalidRows) {}
