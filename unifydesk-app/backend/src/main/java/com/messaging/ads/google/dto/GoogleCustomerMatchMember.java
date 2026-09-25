package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleCustomerMatchMember(
    List<String> emails,
    List<String> phoneNumbers,
    List<String> mobileIds,
    String userId) {}
