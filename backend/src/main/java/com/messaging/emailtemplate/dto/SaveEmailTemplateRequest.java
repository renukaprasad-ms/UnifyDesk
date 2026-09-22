package com.messaging.emailtemplate.dto;

import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import com.messaging.emailtemplate.enums.EmailTemplateEditorMode;
import com.messaging.emailtemplate.enums.EmailTemplatePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SaveEmailTemplateRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull EmailTemplateCategory category,
    @NotNull EmailTemplatePurpose purpose,
    @NotNull EmailTemplateEditorMode editorMode,
    @Size(max = 998) String subject,
    @Size(max = 255) String preheader,
    @Size(max = 500000) String documentJson,
    @Size(max = 750000) String htmlSource,
    Map<String, String> previewValues) {}
