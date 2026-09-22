package com.messaging.template.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.template.dto.*;
import com.messaging.template.entity.MessageTemplate;
import com.messaging.template.enums.*;
import com.messaging.template.repository.MessageTemplateRepository;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageTemplateService {

  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

  private final MessageTemplateRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<MessageTemplateResponse> list(
      Long companyId, ChannelType channelType, boolean includeArchived) {
    List<MessageTemplate> templates =
        includeArchived
            ? repository.findAll().stream()
                .filter(template -> Objects.equals(template.getCompanyId(), companyId))
                .filter(template -> channelType == null || template.getChannelType() == channelType)
                .sorted(Comparator.comparing(MessageTemplate::getUpdatedAt).reversed())
                .toList()
            : channelType == null
                ? repository.findByCompanyIdAndStatusNotOrderByUpdatedAtDesc(
                    companyId, MessageTemplateStatus.ARCHIVED)
                : repository.findByCompanyIdAndChannelTypeAndStatusNotOrderByUpdatedAtDesc(
                    companyId, channelType, MessageTemplateStatus.ARCHIVED);

    return templates.stream().map(this::response).toList();
  }

  @Transactional(readOnly = true)
  public List<MessageTemplateResponse> active(Long companyId, ChannelType channelType) {
    return repository
        .findByCompanyIdAndChannelTypeAndStatusInOrderByUpdatedAtDesc(
            companyId, channelType, List.of(MessageTemplateStatus.ACTIVE))
        .stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public MessageTemplateResponse get(Long companyId, Long templateId) {
    return response(require(companyId, templateId));
  }

  @Transactional
  public MessageTemplateResponse create(
      Long companyId, Long userId, CreateMessageTemplateRequest request) {
    String name = normalizeName(request.name());
    ensureUniqueName(companyId, request.channelType(), name, null);
    validateContent(
        request.channelType(),
        request.category(),
        request.subject(),
        request.headerText(),
        request.bodyText(),
        request.bodyHtml(),
        request.footerText(),
        request.buttons());

    MessageTemplate template = new MessageTemplate();
    template.setCompanyId(companyId);
    template.setCreatedByUserId(userId);
    template.setName(name);
    template.setChannelType(request.channelType());
    template.setCategory(request.category());
    template.setStatus(MessageTemplateStatus.DRAFT);
    template.setProviderStatus(MessageTemplateProviderStatus.LOCAL_ONLY);
    applyContent(
        template,
        request.language(),
        request.subject(),
        request.headerText(),
        request.bodyText(),
        request.bodyHtml(),
        request.footerText(),
        request.buttons());
    return response(repository.save(template));
  }

  @Transactional
  public MessageTemplateResponse update(
      Long companyId, Long templateId, UpdateMessageTemplateRequest request) {
    MessageTemplate template = require(companyId, templateId);
    if (template.getStatus() == MessageTemplateStatus.ARCHIVED) {
      throw new BadRequestException("Archived templates cannot be edited");
    }

    String name = normalizeName(request.name());
    ensureUniqueName(companyId, template.getChannelType(), name, templateId);
    validateContent(
        template.getChannelType(),
        request.category(),
        request.subject(),
        request.headerText(),
        request.bodyText(),
        request.bodyHtml(),
        request.footerText(),
        request.buttons());

    template.setName(name);
    template.setCategory(request.category());
    applyContent(
        template,
        request.language(),
        request.subject(),
        request.headerText(),
        request.bodyText(),
        request.bodyHtml(),
        request.footerText(),
        request.buttons());
    return response(repository.save(template));
  }

  @Transactional
  public MessageTemplateResponse activate(Long companyId, Long templateId) {
    MessageTemplate template = require(companyId, templateId);
    if (template.getStatus() == MessageTemplateStatus.ARCHIVED) {
      throw new BadRequestException("Archived templates cannot be activated");
    }
    if (template.getChannelType() == ChannelType.WHATSAPP
        && template.getProviderStatus() != MessageTemplateProviderStatus.APPROVED) {
      throw new BadRequestException(
          "WhatsApp templates must be approved by Meta before they can be activated");
    }
    template.setStatus(MessageTemplateStatus.ACTIVE);
    return response(repository.save(template));
  }

  @Transactional
  public MessageTemplateResponse moveToDraft(Long companyId, Long templateId) {
    MessageTemplate template = require(companyId, templateId);
    if (template.getStatus() == MessageTemplateStatus.ARCHIVED) {
      throw new BadRequestException("Archived templates cannot be moved to draft");
    }
    template.setStatus(MessageTemplateStatus.DRAFT);
    return response(repository.save(template));
  }

  @Transactional
  public void archive(Long companyId, Long templateId) {
    MessageTemplate template = require(companyId, templateId);
    template.setStatus(MessageTemplateStatus.ARCHIVED);
    template.setArchivedAt(Instant.now());
    repository.save(template);
  }

  public MessageTemplate require(Long companyId, Long templateId) {
    return repository
        .findByIdAndCompanyId(templateId, companyId)
        .orElseThrow(() -> new NotFoundException("Template not found"));
  }

  private void ensureUniqueName(
      Long companyId, ChannelType channelType, String name, Long excludeId) {
    boolean exists =
        excludeId == null
            ? repository.existsByCompanyIdAndChannelTypeAndNameIgnoreCaseAndStatusNot(
                companyId, channelType, name, MessageTemplateStatus.ARCHIVED)
            : repository
                .existsByCompanyIdAndChannelTypeAndNameIgnoreCaseAndStatusNotAndIdNot(
                    companyId,
                    channelType,
                    name,
                    MessageTemplateStatus.ARCHIVED,
                    excludeId);
    if (exists) {
      throw new BadRequestException(
          "A " + channelType.name().toLowerCase(Locale.ROOT) + " template with this name already exists");
    }
  }

  private void validateContent(
      ChannelType channelType,
      MessageTemplateCategory category,
      String subject,
      String headerText,
      String bodyText,
      String bodyHtml,
      String footerText,
      List<MessageTemplateButton> buttons) {
    if (bodyText == null || bodyText.isBlank()) {
      throw new BadRequestException("Template body is required");
    }

    switch (channelType) {
      case EMAIL -> {
        if (headerText != null && !headerText.isBlank()) {
          throw new BadRequestException("Email templates do not use WhatsApp header text");
        }
        if (footerText != null && !footerText.isBlank()) {
          throw new BadRequestException("Email templates do not use WhatsApp footer text");
        }
        if (buttons != null && !buttons.isEmpty()) {
          throw new BadRequestException("Email template buttons are not supported yet");
        }
      }
      case SMS -> {
        if (subject != null && !subject.isBlank()) {
          throw new BadRequestException("SMS templates do not use a subject");
        }
        if (bodyHtml != null && !bodyHtml.isBlank()) {
          throw new BadRequestException("SMS templates do not use HTML");
        }
        if ((headerText != null && !headerText.isBlank())
            || (footerText != null && !footerText.isBlank())
            || (buttons != null && !buttons.isEmpty())) {
          throw new BadRequestException("SMS templates only use message text");
        }
        if (bodyText.length() > 4096) {
          throw new BadRequestException("SMS template body is too long");
        }
      }
      case WHATSAPP -> {
        if (subject != null && !subject.isBlank()) {
          throw new BadRequestException("WhatsApp templates do not use an email subject");
        }
        if (bodyHtml != null && !bodyHtml.isBlank()) {
          throw new BadRequestException("WhatsApp templates do not use HTML");
        }
        if (headerText != null && headerText.length() > 60) {
          throw new BadRequestException("WhatsApp text header must be 60 characters or fewer");
        }
        if (footerText != null && footerText.length() > 60) {
          throw new BadRequestException("WhatsApp footer must be 60 characters or fewer");
        }
        if (bodyText.length() > 4096) {
          throw new BadRequestException("WhatsApp template body must be 4096 characters or fewer");
        }
        if (buttons != null && buttons.size() > 3) {
          throw new BadRequestException("WhatsApp templates support at most 3 buttons in this editor");
        }
        if (category == MessageTemplateCategory.TRANSACTIONAL) {
          throw new BadRequestException(
              "Use MARKETING, UTILITY or AUTHENTICATION for WhatsApp templates");
        }
      }
    }
  }

  private void applyContent(
      MessageTemplate template,
      String language,
      String subject,
      String headerText,
      String bodyText,
      String bodyHtml,
      String footerText,
      List<MessageTemplateButton> buttons) {
    template.setLanguage(
        normalizeLanguage(language, template.getChannelType()));
    template.setSubject(trimToNull(subject));
    template.setHeaderText(trimToNull(headerText));
    template.setBodyText(bodyText.trim());
    template.setBodyHtml(trimToNull(bodyHtml));
    template.setFooterText(trimToNull(footerText));
    template.setButtonsJson(writeJson(buttons == null ? List.of() : buttons));
    template.setVariablesJson(
        writeJson(extractVariables(subject, headerText, bodyText, bodyHtml, footerText)));
  }

  private List<String> extractVariables(String... values) {
    LinkedHashSet<String> variables = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      Matcher matcher = VARIABLE_PATTERN.matcher(value);
      while (matcher.find()) {
        variables.add(matcher.group(1));
      }
    }
    return List.copyOf(variables);
  }

  private MessageTemplateResponse response(MessageTemplate template) {
    return MessageTemplateResponse.from(
        template,
        readButtons(template.getButtonsJson()),
        readStrings(template.getVariablesJson()));
  }

  private List<MessageTemplateButton> readButtons(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(
          json, new TypeReference<List<MessageTemplateButton>>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  private List<String> readStrings(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize template metadata", exception);
    }
  }

  private String normalizeName(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      throw new BadRequestException("Template name is required");
    }
    return normalized;
  }

  private String normalizeLanguage(String value, ChannelType channelType) {
    if (value == null || value.isBlank()) {
      return channelType == ChannelType.WHATSAPP ? "en_US" : "en";
    }
    return value.trim();
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
