package com.messaging.channel.gmail.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GmailSyncRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate) {}
