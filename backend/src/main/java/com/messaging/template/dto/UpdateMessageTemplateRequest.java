package com.messaging.template.dto;

import com.messaging.template.enums.MessageTemplateCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateMessageTemplateRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull MessageTemplateCategory category,
    @Size(max = 32) String language,
    @Size(max = 998) String subject,
    @Size(max = 120) String headerText,
    @NotBlank @Size(max = 500000) String bodyText,
    @Size(max = 1000000) String bodyHtml,
    @Size(max = 120) String footerText,
    List<@Valid MessageTemplateButton> buttons,
    @Size(max = 1000000) String designJson) {}
