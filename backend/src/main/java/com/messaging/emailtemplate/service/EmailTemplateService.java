package com.messaging.emailtemplate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.emailtemplate.dto.*;
import com.messaging.emailtemplate.entity.*;
import com.messaging.emailtemplate.enums.*;
import com.messaging.emailtemplate.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("companyEmailTemplateService")
@RequiredArgsConstructor
public class EmailTemplateService {

  private final EmailTemplateRepository templateRepository;
  private final EmailTemplateVersionRepository versionRepository;
  private final EmailTemplateCatalogRepository catalogRepository;
  private final EmailTemplateCompiler compiler;
  private final EmailVariableService variableService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public Page<EmailTemplateSummaryResponse> list(
      Long companyId,
      String query,
      EmailTemplateCategory category,
      EmailTemplateStatus status,
      boolean includeArchived,
      Pageable pageable) {
    Page<EmailTemplate> page =
        templateRepository.search(
            companyId,
            trimToNull(query),
            category,
            status,
            includeArchived,
            pageable);

    Set<Long> versionIds = new HashSet<>();
    for (EmailTemplate template : page.getContent()) {
      if (template.getCurrentVersionId() != null) versionIds.add(template.getCurrentVersionId());
      if (template.getPublishedVersionId() != null) versionIds.add(template.getPublishedVersionId());
    }
    Map<Long, EmailTemplateVersion> versions =
        versionRepository.findAllByIdIn(versionIds).stream()
            .collect(Collectors.toMap(EmailTemplateVersion::getId, Function.identity()));

    return page.map(
        template -> {
          EmailTemplateVersion current = versions.get(template.getCurrentVersionId());
          EmailTemplateVersion published = versions.get(template.getPublishedVersionId());
          return EmailTemplateSummaryResponse.from(
              template, current, published, variables(current));
        });
  }

  @Transactional(readOnly = true)
  public List<EmailTemplateSummaryResponse> published(Long companyId) {
    List<EmailTemplate> templates =
        templateRepository.findByCompanyIdAndStatusOrderByUpdatedAtDesc(
            companyId, EmailTemplateStatus.PUBLISHED);
    Set<Long> ids =
        templates.stream()
            .map(EmailTemplate::getPublishedVersionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, EmailTemplateVersion> versions =
        versionRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(EmailTemplateVersion::getId, Function.identity()));
    return templates.stream()
        .map(
            t -> {
              EmailTemplateVersion v = versions.get(t.getPublishedVersionId());
              return EmailTemplateSummaryResponse.from(t, v, v, variables(v));
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public EmailTemplateResponse get(Long companyId, Long templateId) {
    EmailTemplate template = require(companyId, templateId);
    EmailTemplateVersion current = version(template.getCurrentVersionId(), templateId);
    EmailTemplateVersion published =
        template.getPublishedVersionId() == null
            ? null
            : version(template.getPublishedVersionId(), templateId);
    return EmailTemplateResponse.from(template, current, published, variables(current));
  }

  @Transactional
  public EmailTemplateResponse create(
      Long companyId, Long userId, SaveEmailTemplateRequest request) {
    String name = normalizeName(request.name());
    ensureUniqueName(companyId, name, null);

    EmailTemplate template = new EmailTemplate();
    template.setCompanyId(companyId);
    template.setName(name);
    template.setCategory(request.category());
    template.setPurpose(request.purpose());
    template.setCreatedByUserId(userId);
    template.setStatus(EmailTemplateStatus.DRAFT);
    template = templateRepository.saveAndFlush(template);

    EmailTemplateVersion version = createVersion(template, userId, request, 1);
    template.setCurrentVersionId(version.getId());
    templateRepository.save(template);
    return EmailTemplateResponse.from(template, version, null, variables(version));
  }

  @Transactional
  public EmailTemplateResponse update(
      Long companyId, Long templateId, Long userId, SaveEmailTemplateRequest request) {
    EmailTemplate template = requireForUpdate(companyId, templateId);
    requireEditable(template);
    String name = normalizeName(request.name());
    ensureUniqueName(companyId, name, templateId);

    EmailTemplateVersion current = version(template.getCurrentVersionId(), templateId);
    EmailTemplateVersion next =
        createVersion(template, userId, request, current.getVersionNumber() + 1);

    template.setName(name);
    template.setCategory(request.category());
    template.setPurpose(request.purpose());
    template.setCurrentVersionId(next.getId());
    if (template.getPublishedVersionId() == null) {
      template.setStatus(EmailTemplateStatus.DRAFT);
    }
    templateRepository.save(template);

    EmailTemplateVersion published =
        template.getPublishedVersionId() == null
            ? null
            : version(template.getPublishedVersionId(), templateId);
    return EmailTemplateResponse.from(template, next, published, variables(next));
  }

  @Transactional
  public EmailTemplateResponse cloneFromCatalog(
      Long companyId, Long catalogId, Long userId, String requestedName) {
    EmailTemplateCatalogItem item =
        catalogRepository
            .findById(catalogId)
            .filter(EmailTemplateCatalogItem::isActive)
            .orElseThrow(() -> new NotFoundException("Catalog template not found"));
    String name = availableName(companyId, trimToNull(requestedName) == null ? item.getName() : requestedName);
    return create(
        companyId,
        userId,
        new SaveEmailTemplateRequest(
            name,
            item.getCategory(),
            item.getPurpose(),
            item.getEditorMode(),
            item.getSubjectTemplate(),
            item.getPreheaderTemplate(),
            item.getDocumentJson(),
            item.getHtmlSource(),
            Map.of()));
  }

  @Transactional
  public EmailTemplateResponse publish(Long companyId, Long templateId) {
    EmailTemplate template = requireForUpdate(companyId, templateId);
    requireEditable(template);
    EmailTemplateVersion current = version(template.getCurrentVersionId(), templateId);
    current.setPublishedAt(Instant.now());
    versionRepository.save(current);
    template.setPublishedVersionId(current.getId());
    template.setStatus(EmailTemplateStatus.PUBLISHED);
    templateRepository.save(template);
    return EmailTemplateResponse.from(template, current, current, variables(current));
  }

  @Transactional
  public void archive(Long companyId, Long templateId) {
    EmailTemplate template = requireForUpdate(companyId, templateId);
    if (template.getStatus() == EmailTemplateStatus.ARCHIVED) return;
    template.setStatus(EmailTemplateStatus.ARCHIVED);
    template.setArchivedAt(Instant.now());
    templateRepository.save(template);
  }

  @Transactional(readOnly = true)
  public List<EmailTemplateVersionResponse> versions(Long companyId, Long templateId) {
    require(companyId, templateId);
    return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId).stream()
        .map(EmailTemplateVersionResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public EmailTemplatePreviewResponse preview(SaveEmailTemplateRequest request) {
    EmailTemplateCompiler.Compiled compiled =
        compiler.compile(
            request.editorMode(),
            request.subject(),
            request.preheader(),
            request.documentJson(),
            request.htmlSource());

    Map<String, String> values =
        request.previewValues() == null ? Map.of() : request.previewValues();
    EmailVariableService.Rendered subject = variableService.render(request.subject(), values);
    EmailVariableService.Rendered preheader = variableService.render(request.preheader(), values);
    EmailVariableService.Rendered html = variableService.render(compiled.html(), values);
    EmailVariableService.Rendered text = variableService.render(compiled.text(), values);

    LinkedHashSet<String> unresolved = new LinkedHashSet<>();
    unresolved.addAll(subject.unresolvedVariables());
    unresolved.addAll(preheader.unresolvedVariables());
    unresolved.addAll(html.unresolvedVariables());
    unresolved.addAll(text.unresolvedVariables());

    return new EmailTemplatePreviewResponse(
        subject.value(),
        preheader.value(),
        html.value(),
        text.value(),
        compiled.variables(),
        List.copyOf(unresolved));
  }

  @Transactional(readOnly = true)
  public PublishedEmail requirePublishedForBroadcast(Long companyId, Long templateId) {
    EmailTemplate template = require(companyId, templateId);
    if (template.getStatus() != EmailTemplateStatus.PUBLISHED
        || template.getPublishedVersionId() == null) {
      throw new BadRequestException("Choose a published email template");
    }
    EmailTemplateVersion published = version(template.getPublishedVersionId(), templateId);
    return new PublishedEmail(
        template.getId(),
        published.getId(),
        published.getVersionNumber(),
        published.getPurpose(),
        published.getSubjectTemplate(),
        published.getCompiledText(),
        published.getCompiledHtml(),
        published.getContentHash(),
        variables(published));
  }

  private EmailTemplateVersion createVersion(
      EmailTemplate template,
      Long userId,
      SaveEmailTemplateRequest request,
      long versionNumber) {
    EmailTemplateCompiler.Compiled compiled =
        compiler.compile(
            request.editorMode(),
            request.subject(),
            request.preheader(),
            request.documentJson(),
            request.htmlSource());

    EmailTemplateVersion version = new EmailTemplateVersion();
    version.setTemplateId(template.getId());
    version.setVersionNumber(versionNumber);
    version.setEditorMode(request.editorMode());
    version.setPurpose(request.purpose());
    version.setSubjectTemplate(trimToNull(request.subject()));
    version.setPreheaderTemplate(trimToNull(request.preheader()));
    version.setDocumentJson(
        request.editorMode() == EmailTemplateEditorMode.BUILDER
            ? request.documentJson().trim()
            : null);
    version.setHtmlSource(
        request.editorMode() == EmailTemplateEditorMode.HTML
            ? request.htmlSource().trim()
            : null);
    version.setCompiledHtml(compiled.html());
    version.setCompiledText(compiled.text());
    version.setVariablesJson(writeJson(compiled.variables()));
    version.setContentHash(compiled.contentHash());
    version.setCreatedByUserId(userId);
    return versionRepository.saveAndFlush(version);
  }

  private EmailTemplate require(Long companyId, Long templateId) {
    return templateRepository
        .findByIdAndCompanyId(templateId, companyId)
        .orElseThrow(() -> new NotFoundException("Email template not found"));
  }

  private EmailTemplate requireForUpdate(Long companyId, Long templateId) {
    return templateRepository
        .findForUpdate(templateId, companyId)
        .orElseThrow(() -> new NotFoundException("Email template not found"));
  }

  private EmailTemplateVersion version(Long versionId, Long templateId) {
    if (versionId == null) throw new NotFoundException("Email template version not found");
    return versionRepository
        .findByIdAndTemplateId(versionId, templateId)
        .orElseThrow(() -> new NotFoundException("Email template version not found"));
  }

  private void requireEditable(EmailTemplate template) {
    if (template.getStatus() == EmailTemplateStatus.ARCHIVED) {
      throw new BadRequestException("Archived email templates cannot be edited");
    }
  }

  private void ensureUniqueName(Long companyId, String name, Long excludeId) {
    boolean exists =
        excludeId == null
            ? templateRepository.existsByCompanyIdAndNameIgnoreCaseAndStatusNot(
                companyId, name, EmailTemplateStatus.ARCHIVED)
            : templateRepository.existsByCompanyIdAndNameIgnoreCaseAndStatusNotAndIdNot(
                companyId, name, EmailTemplateStatus.ARCHIVED, excludeId);
    if (exists) throw new BadRequestException("An email template with this name already exists");
  }

  private String availableName(Long companyId, String base) {
    String normalized = normalizeName(base);
    if (!templateRepository.existsByCompanyIdAndNameIgnoreCaseAndStatusNot(
        companyId, normalized, EmailTemplateStatus.ARCHIVED)) return normalized;
    for (int suffix = 2; suffix <= 100; suffix++) {
      String candidate = normalized + " " + suffix;
      if (!templateRepository.existsByCompanyIdAndNameIgnoreCaseAndStatusNot(
          companyId, candidate, EmailTemplateStatus.ARCHIVED)) return candidate;
    }
    throw new BadRequestException("Unable to choose an available template name");
  }

  private List<String> variables(EmailTemplateVersion version) {
    if (version == null || version.getVariablesJson() == null || version.getVariablesJson().isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          version.getVariablesJson(), new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize email template metadata", exception);
    }
  }

  private String normalizeName(String value) {
    String name = trimToNull(value);
    if (name == null) throw new BadRequestException("Email template name is required");
    return name;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record PublishedEmail(
      Long templateId,
      Long versionId,
      long version,
      EmailTemplatePurpose purpose,
      String subject,
      String bodyText,
      String bodyHtml,
      String contentHash,
      List<String> variables) {}
}
