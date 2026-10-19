package com.messaging.managedsms.control.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.*;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsTemplateService {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{#var#\\}");

  private final ManagedSmsTemplateRepository templateRepository;
  private final ManagedSmsTemplateVersionRepository versionRepository;
  private final ManagedSmsTemplateHeaderRepository templateHeaderRepository;
  private final ManagedSmsProviderBindingRepository bindingRepository;
  private final ManagedSmsHeaderService headerService;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsJsonSupport jsonSupport;
  private final ManagedSmsEligibilityService eligibilityService;
  private final ManagedSmsCtaPolicyService ctaPolicyService;

  @Transactional(readOnly = true)
  public List<TemplateResponse> list(Long companyId) {
    return templateRepository
        .findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(companyId)
        .stream()
        .map(template -> response(companyId, template))
        .toList();
  }

  @Transactional(readOnly = true)
  public TemplateResponse get(Long companyId, Long templateId) {
    return response(companyId, requireTemplate(companyId, templateId));
  }

  @Transactional
  public TemplateResponse create(Long companyId, Long userId, TemplateRequest request) {
    ManagedSmsTemplate template = new ManagedSmsTemplate();
    template.setCompanyId(companyId);
    template.setName(request.name().trim());
    template.setPurpose(request.messageCategory());
    template.setStatus(TemplateStatus.DRAFT);
    template.setCreatedByUserId(userId);
    ManagedSmsTemplate saved = templateRepository.save(template);
    createVersion(
        companyId,
        saved,
        request.headerIds(),
        request.dltTemplateId(),
        request.bodyText(),
        request.variables(),
        request.ctaPolicy(),
        request.consentPolicy(),
        request.optOutPolicy());
    return response(companyId, saved);
  }

  @Transactional
  public TemplateResponse createVersion(
      Long companyId, Long templateId, TemplateVersionRequest request) {
    ManagedSmsTemplate template = requireTemplate(companyId, templateId);
    createVersion(
        companyId,
        template,
        request.headerIds(),
        request.dltTemplateId(),
        request.bodyText(),
        request.variables(),
        request.ctaPolicy(),
        request.consentPolicy(),
        request.optOutPolicy());
    return response(companyId, template);
  }

  @Transactional
  public TemplateResponse submit(Long companyId, Long templateId, Long versionId) {
    ManagedSmsTemplate template = requireTemplate(companyId, templateId);
    ManagedSmsTemplateVersion version = requireVersion(companyId, templateId, versionId);
    if (version.getStatus() == TemplateVersionStatus.ACTIVE
        || version.getStatus() == TemplateVersionStatus.SUPERSEDED) {
      throw new BadRequestException(
          "MANAGED_SMS_TEMPLATE_NOT_READY: Activated template content is immutable");
    }
    version.setDltStatus(DltTemplateStatus.PENDING_DLT_APPROVAL);
    version.setStatus(TemplateVersionStatus.PENDING_DLT_APPROVAL);
    versionRepository.save(version);
    template.setStatus(TemplateStatus.DRAFT);
    templateRepository.save(template);
    eligibilityService.recalculate(companyId);
    return response(companyId, template);
  }

  @Transactional
  public TemplateResponse refresh(Long companyId, Long templateId, Long versionId) {
    ManagedSmsTemplate template = requireTemplate(companyId, templateId);
    ManagedSmsTemplateVersion version = requireVersion(companyId, templateId, versionId);
    if (version.getDltStatus() != DltTemplateStatus.APPROVED_DLT) {
      throw new BadRequestException(
          "MANAGED_SMS_TEMPLATE_NOT_READY: DLT approval must be recorded before provider refresh");
    }
    ManagedSmsTenant tenant =
        tenantRepository
            .findByCompanyId(companyId)
            .orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
    for (ManagedSmsTemplateHeader link :
        templateHeaderRepository.findByCompanyIdAndIdTemplateVersionId(companyId, versionId)) {
      ManagedSmsProviderBinding binding =
          bindingRepository
              .findByCompanyIdAndTemplateVersionIdAndHeaderIdAndProvider(
                  companyId,
                  versionId,
                  link.getId().getHeaderId(),
                  BroadcastSenderProvider.MSG91_SMS)
              .orElseGet(
                  () ->
                      newBinding(companyId, tenant.getId(), versionId, link.getId().getHeaderId()));
      binding.setStatus(ProviderBindingStatus.SYNCING);
      bindingRepository.save(binding);
      provisioningService.enqueueOrReset(
          companyId,
          tenant.getId(),
          ProvisioningResourceType.TEMPLATE_BINDING,
          binding.getId(),
          ProvisioningOperationType.REFRESH_TEMPLATE_BINDING,
          "managed-sms:template-binding:refresh:" + binding.getId());
    }
    version.setStatus(TemplateVersionStatus.PROVIDER_SYNCING);
    versionRepository.save(version);
    return response(companyId, template);
  }

  @Transactional
  public TemplateResponse archive(Long companyId, Long templateId, Long versionId) {
    ManagedSmsTemplate template = requireTemplate(companyId, templateId);
    ManagedSmsTemplateVersion version = requireVersion(companyId, templateId, versionId);
    version.setStatus(TemplateVersionStatus.ARCHIVED);
    versionRepository.save(version);
    bindingRepository
        .findByCompanyIdAndTemplateVersionId(companyId, versionId)
        .forEach(
            binding -> {
              binding.setStatus(ProviderBindingStatus.ARCHIVED);
              bindingRepository.save(binding);
            });
    boolean anyActive =
        versionRepository
            .findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
                templateId, companyId, TemplateVersionStatus.ACTIVE)
            .isPresent();
    template.setStatus(anyActive ? TemplateStatus.ACTIVE : TemplateStatus.DRAFT);
    templateRepository.save(template);
    eligibilityService.recalculate(companyId);
    return response(companyId, template);
  }

  @Transactional(readOnly = true)
  public PreviewResponse preview(Long companyId, Long templateId, PreviewRequest request) {
    ManagedSmsTemplate template = requireTemplate(companyId, templateId);
    Long headerId = parseId(request.headerId(), "headerId");
    ManagedSmsHeader header = headerService.require(companyId, headerId);
    ManagedSmsTemplateVersion version =
        versionRepository
            .findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
                template.getId(), companyId, TemplateVersionStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "MANAGED_SMS_TEMPLATE_NOT_READY: No active template version"));
    boolean allowed =
        templateHeaderRepository
            .findByCompanyIdAndIdTemplateVersionId(companyId, version.getId())
            .stream()
            .anyMatch(link -> Objects.equals(link.getId().getHeaderId(), header.getId()));
    if (!allowed) {
      throw new BadRequestException(
          "MANAGED_SMS_TEMPLATE_HEADER_MISMATCH: Header is not approved for this template");
    }
    List<String> errors = new ArrayList<>();
    List<VariableDefinition> schema = jsonSupport.variables(version.getVariablesSchemaJson());
    Map<String, String> ctaErrors =
        ctaPolicyService.validateRuntimeValues(
            version.getCtaPolicyJson(), schema, request.variables());
    String rendered = render(version, schema, request.variables(), ctaErrors, errors);
    return new PreviewResponse(rendered, List.copyOf(errors));
  }

  ManagedSmsTemplateVersion requireVersion(Long companyId, Long templateId, Long versionId) {
    ManagedSmsTemplateVersion version =
        versionRepository
            .findByIdAndCompanyId(versionId, companyId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "MANAGED_SMS_TEMPLATE_NOT_READY: Template version not found"));
    if (!Objects.equals(version.getTemplateId(), templateId)) {
      throw new NotFoundException("MANAGED_SMS_TEMPLATE_NOT_READY: Template version not found");
    }
    return version;
  }

  public TemplateResponse response(Long companyId, ManagedSmsTemplate template) {
    ManagedSmsTemplateVersion latest =
        versionRepository
            .findFirstByTemplateIdAndCompanyIdOrderByVersionNoDesc(template.getId(), companyId)
            .orElse(null);
    ManagedSmsTemplateVersion active =
        versionRepository
            .findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
                template.getId(), companyId, TemplateVersionStatus.ACTIVE)
            .orElse(null);
    return new TemplateResponse(
        template.getId().toString(),
        template.getName(),
        template.getPurpose(),
        template.getStatus(),
        active == null ? null : versionResponse(companyId, active),
        latest == null ? null : versionResponse(companyId, latest),
        template.getUpdatedAt());
  }

  public TemplateVersionResponse versionResponse(
      Long companyId, ManagedSmsTemplateVersion version) {
    List<String> headerIds =
        templateHeaderRepository
            .findByCompanyIdAndIdTemplateVersionId(companyId, version.getId())
            .stream()
            .map(link -> link.getId().getHeaderId().toString())
            .toList();
    List<ManagedSmsProviderBinding> bindings =
        bindingRepository.findByCompanyIdAndTemplateVersionId(companyId, version.getId());
    String sync =
        bindings.isEmpty()
            ? "NOT_STARTED"
            : bindings.stream().allMatch(item -> item.getStatus() == ProviderBindingStatus.ACTIVE)
                ? "READY"
                : bindings.stream()
                        .anyMatch(
                            item ->
                                item.getStatus() == ProviderBindingStatus.ACTION_REQUIRED
                                    || item.getStatus() == ProviderBindingStatus.FAILED)
                    ? "FAILED"
                    : "SYNCING";
    return new TemplateVersionResponse(
        version.getId().toString(),
        version.getVersionNo(),
        version.getDltTemplateId(),
        version.getBodyText(),
        jsonSupport.variables(version.getVariablesSchemaJson()),
        headerIds,
        version.getStatus(),
        sync,
        version.getCheckedAt(),
        version.getStaleAfter(),
        version.getLockVersion());
  }

  private ManagedSmsTemplateVersion createVersion(
      Long companyId,
      ManagedSmsTemplate template,
      List<String> headerIds,
      String dltTemplateId,
      String bodyText,
      List<VariableDefinition> variables,
      JsonNode ctaPolicy,
      JsonNode consentPolicy,
      JsonNode optOutPolicy) {
    String normalizedDltId = dltTemplateId.trim();
    if (versionRepository.existsByCompanyIdAndDltTemplateId(companyId, normalizedDltId)) {
      throw new ConflictException(
          "MANAGED_SMS_TEMPLATE_NOT_READY: This DLT Template ID already exists");
    }
    List<ManagedSmsHeader> headers =
        headerIds.stream()
            .map(value -> headerService.require(companyId, parseId(value, "headerId")))
            .toList();
    if (headers.stream()
        .anyMatch(
            header ->
                header.getStatus() != HeaderStatus.ACTIVE
                    || header.getProviderMappingStatus() != ProviderMappingStatus.READY
                    || header.getMessageCategory() != template.getPurpose())) {
      throw new BadRequestException(
          "MANAGED_SMS_TEMPLATE_HEADER_MISMATCH: Every Header must be active and match the template category");
    }
    validateSchema(bodyText, variables, ctaPolicy);
    long nextVersion =
        versionRepository
            .findFirstByTemplateIdAndCompanyIdOrderByVersionNoDesc(template.getId(), companyId)
            .map(item -> item.getVersionNo() + 1)
            .orElse(1L);
    ManagedSmsTemplateVersion version = new ManagedSmsTemplateVersion();
    version.setCompanyId(companyId);
    version.setTemplateId(template.getId());
    version.setVersionNo(nextVersion);
    version.setDltTemplateId(normalizedDltId);
    version.setBodyText(bodyText);
    version.setBodyFingerprint(sha256(bodyText));
    version.setVariablesSchemaJson(jsonSupport.variables(variables));
    version.setCtaPolicyJson(jsonSupport.json(ctaPolicy, "{}"));
    version.setConsentPolicyJson(
        consentPolicy == null ? null : jsonSupport.json(consentPolicy, "{}"));
    version.setOptOutPolicyJson(optOutPolicy == null ? null : jsonSupport.json(optOutPolicy, "{}"));
    version.setRulesetVersion("INDIA_TCCCPR_2025");
    version.setDltStatus(DltTemplateStatus.DRAFT);
    version.setStatus(TemplateVersionStatus.DRAFT);
    ManagedSmsTemplateVersion saved = versionRepository.save(version);
    for (ManagedSmsHeader header : headers) {
      ManagedSmsTemplateHeader link = new ManagedSmsTemplateHeader();
      link.setId(new ManagedSmsTemplateHeaderId(saved.getId(), header.getId()));
      link.setCompanyId(companyId);
      templateHeaderRepository.save(link);
    }
    return saved;
  }

  private void validateSchema(
      String bodyText, List<VariableDefinition> variables, JsonNode ctaPolicy) {
    long placeholders = PLACEHOLDER.matcher(bodyText).results().count();
    if (placeholders != variables.size()) {
      throw new BadRequestException(
          "MANAGED_SMS_VARIABLE_INVALID: Variable definitions must match DLT placeholders");
    }
    Set<String> keys = new HashSet<>();
    for (VariableDefinition variable : variables) {
      if (!keys.add(variable.key())) {
        throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID: Duplicate variable key");
      }
    }
    if (ctaPolicy != null && ctaPolicy.isObject()) {
      validateCtaList(ctaPolicy.get("urlVariables"), keys, variables, VariableType.URL);
      validateCtaList(ctaPolicy.get("callbackVariables"), keys, variables, VariableType.CALLBACK);
    }
  }

  private void validateCtaList(
      JsonNode node,
      Set<String> keys,
      List<VariableDefinition> variables,
      VariableType requiredType) {
    if (node == null || node.isNull()) return;
    if (!node.isArray()) {
      throw new BadRequestException("MANAGED_SMS_CTA_NOT_APPROVED: CTA variable list is invalid");
    }
    for (JsonNode item : node) {
      String key = item.asText();
      if (!keys.contains(key)
          || variables.stream()
              .noneMatch(
                  variable -> variable.key().equals(key) && variable.type() == requiredType)) {
        throw new BadRequestException(
            "MANAGED_SMS_CTA_NOT_APPROVED: CTA variables must use their approved variable type");
      }
    }
  }

  private String render(
      ManagedSmsTemplateVersion version,
      List<VariableDefinition> schema,
      Map<String, String> values,
      Map<String, String> ctaErrors,
      List<String> errors) {
    Set<String> declared =
        schema.stream().map(VariableDefinition::key).collect(java.util.stream.Collectors.toSet());
    values.keySet().stream()
        .filter(key -> !declared.contains(key))
        .forEach(key -> errors.add("Unknown variable: " + key));

    Matcher matcher = PLACEHOLDER.matcher(version.getBodyText());
    StringBuffer output = new StringBuffer();
    int index = 0;
    while (matcher.find()) {
      if (index >= schema.size()) break;
      VariableDefinition definition = schema.get(index++);
      String value = values.get(definition.key());
      String ctaError = ctaErrors.get(definition.key());
      if (ctaError != null) {
        errors.add(definition.key() + ": " + ctaError);
      }
      String safe = ctaError == null ? validateValue(definition, value, errors) : "";
      matcher.appendReplacement(output, Matcher.quoteReplacement(safe));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  private String validateValue(VariableDefinition definition, String value, List<String> errors) {
    if (value == null || value.isBlank()) {
      errors.add("Missing variable: " + definition.key());
      return "";
    }
    if (value.length() > definition.maxLength()) {
      errors.add("Variable is too long: " + definition.key());
    }
    if (value.contains("{#var#}") || value.chars().anyMatch(Character::isISOControl)) {
      errors.add("Variable contains invalid content: " + definition.key());
    }
    boolean valid =
        switch (definition.type()) {
          case NUMERIC -> value.matches("[0-9]+");
          case CALLBACK -> value.matches("[0-9+() -]{6,30}");
          case EMAIL -> value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
          case URL -> validUrl(value);
          case ALPHANUMERIC -> true;
        };
    if (!valid) errors.add("Variable has invalid type: " + definition.key());
    return value;
  }

  private boolean validUrl(String value) {
    try {
      URI uri = URI.create(value);
      return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null;
    } catch (Exception exception) {
      return false;
    }
  }

  private ManagedSmsProviderBinding newBinding(
      Long companyId, Long tenantId, Long versionId, Long headerId) {
    ManagedSmsProviderBinding binding = new ManagedSmsProviderBinding();
    binding.setCompanyId(companyId);
    binding.setTenantId(tenantId);
    binding.setTemplateVersionId(versionId);
    binding.setHeaderId(headerId);
    binding.setProvider(BroadcastSenderProvider.MSG91_SMS);
    binding.setStatus(ProviderBindingStatus.PENDING);
    return binding;
  }

  private ManagedSmsTemplate requireTemplate(Long companyId, Long templateId) {
    return templateRepository
        .findByIdAndCompanyId(templateId, companyId)
        .filter(item -> item.getArchivedAt() == null)
        .orElseThrow(
            () -> new NotFoundException("MANAGED_SMS_TEMPLATE_NOT_READY: Template not found"));
  }

  private Long parseId(String value, String field) {
    try {
      return Long.valueOf(value);
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY: Invalid " + field);
    }
  }

  private String sha256(String value) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to fingerprint Managed SMS template", exception);
    }
  }
}
