package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleNegativeKeywordBatchRequest(
    @NotEmpty @Size(max = 200) List<@Valid GoogleNegativeKeywordInput> keywords) {}
