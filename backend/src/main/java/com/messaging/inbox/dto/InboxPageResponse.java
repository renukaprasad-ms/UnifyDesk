package com.messaging.inbox.dto;

import java.util.List;

public record InboxPageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {}
