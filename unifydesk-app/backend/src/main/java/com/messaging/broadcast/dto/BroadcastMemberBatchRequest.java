package com.messaging.broadcast.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BroadcastMemberBatchRequest(
    @NotEmpty @Size(max = 1000) List<@Valid BroadcastMemberInput> members) {}
