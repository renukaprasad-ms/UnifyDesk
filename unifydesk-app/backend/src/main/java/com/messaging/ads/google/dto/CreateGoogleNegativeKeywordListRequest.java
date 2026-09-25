package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGoogleNegativeKeywordListRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 200) List<@Valid GoogleNegativeKeywordInput> keywords) {}
