package com.messaging.emailprovider.validation.dto;

import java.util.List;

public record ValidationResultsResponse(
    List<ValidationItemResponse> items, int page, int size, long totalElements, int totalPages) {}
