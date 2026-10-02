package com.messaging.emailtemplate.service;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.msg91.Msg91EmailGateway;
import com.messaging.broadcast.msg91.Msg91EmailTemplateSnapshot;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.emailtemplate.dto.EmailTemplateProviderBindingResponse;
import com.messaging.emailtemplate.entity.EmailTemplate;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import com.messaging.emailtemplate.entity.EmailTemplateVersion;
import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;
import com.messaging.emailtemplate.enums.EmailTemplatePurpose;
import com.messaging.emailtemplate.repository.EmailTemplateProviderBindingRepository;
import com.messaging.emailtemplate.repository.EmailTemplateRepository;
import com.messaging.emailtemplate.repository.EmailTemplateVersionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateProviderBindingService {

  private final EmailTemplateProviderBindingRepository bindingRepository;
  private final EmailTemplateRepository templateRepository;
  private final EmailTemplateVersionRepository versionRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final EmailTemplateService emailTemplateService;
  private final Msg91EmailGateway msg91EmailGateway;

  @Transactional(readOnly = true)
  public List<EmailTemplateProviderBindingResponse> listForTemplate(
      Long companyId, Long templateId) {
    requireTemplate(companyId, templateId);
    return bindingRepository
        .findByCompanyIdAndTemplateIdOrderByUpdatedAtDesc(companyId, templateId)
        .stream()
        .map(EmailTemplateProviderBindingResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EmailTemplateProviderBindingResponse> listForConnection(
      Long companyId, Long connectionId) {
    requireConnection(companyId, connectionId, BroadcastSenderProvider.MSG91_EMAIL);
    return bindingRepository
        .findByCompanyIdAndConnectionIdAndProviderOrderByUpdatedAtDesc(
            companyId, connectionId, BroadcastSenderProvider.MSG91_EMAIL)
        .stream()
        .map(EmailTemplateProviderBindingResponse::from)
        .toList();
  }

  public EmailTemplateProviderBindingResponse syncMsg91(
      Long companyId, Long templateId, Long connectionId) {
    EmailTemplate template = requireTemplate(companyId, templateId);
    EmailTemplateService.PublishedEmail published =
        emailTemplateService.requirePublishedForBroadcast(companyId, templateId);

    if (published.purpose() != EmailTemplatePurpose.TRANSACTIONAL) {
      throw new BadRequestException(
          "MSG91 Email supports transactional templates only. Change the template purpose only if the message is genuinely transactional.");
    }
    if (published.subject() == null || published.subject().isBlank()) {
      throw new BadRequestException("MSG91 Email templates require a subject before sync");
    }
    if (published.bodyHtml() == null || published.bodyHtml().isBlank()) {
      throw new BadRequestException("MSG91 Email templates require compiled HTML before sync");
    }

    BroadcastSenderConnection connection =
        requireConnection(companyId, connectionId, BroadcastSenderProvider.MSG91_EMAIL);
    requireUsableConnection(connection);

    String slug = providerSlug(templateId, published.version(), published.contentHash());

    EmailTemplateProviderBinding binding =
        bindingRepository
            .findByTemplateVersionIdAndConnectionIdAndProvider(
                published.versionId(), connectionId, BroadcastSenderProvider.MSG91_EMAIL)
            .orElseGet(
                () ->
                    newBinding(
                        companyId,
                        templateId,
                        published.versionId(),
                        published.version(),
                        connectionId,
                        slug,
                        published));

    if (binding.getProviderTemplateId() != null
        && !binding.getProviderTemplateId().isBlank()
        && binding.getContentHash().equals(published.contentHash())) {
      return refreshInternal(binding, connection);
    }

    binding.setProviderTemplateSlug(slug);
    binding.setContentHash(published.contentHash());
    binding.setStatus(EmailTemplateProviderBindingStatus.SYNCING);
    binding.setProviderStatus(null);
    binding.setErrorCode(null);
    binding.setErrorMessage(null);
    binding = saveBinding(binding);

    try {
      Msg91EmailTemplateSnapshot snapshot =
          msg91EmailGateway.createTemplate(
              connection,
              providerName(template.getName(), published.version()),
              slug,
              published.subject(),
              msg91BroadcastHtml(published.bodyHtml()));
      if (snapshot.providerStatus() == null
          || snapshot.providerStatus().isBlank()
          || snapshot.status() == EmailTemplateProviderBindingStatus.SYNCED) {
        Msg91EmailTemplateSnapshot refreshed =
            msg91EmailGateway.findTemplate(
                connection, snapshot.providerTemplateId(), snapshot.slug());
        if (refreshed != null) snapshot = refreshed;
      }
      applySnapshot(binding, snapshot);
      Instant now = Instant.now();
      binding.setSyncedAt(now);
      binding.setLastCheckedAt(now);
      EmailTemplateProviderBinding saved = bindingRepository.save(binding);
      log.info(
          "msg91_template_sync companyId={} templateId={} version={} connectionId={} status={} providerStatus={}",
          companyId,
          templateId,
          published.version(),
          connectionId,
          saved.getStatus(),
          saved.getProviderStatus());
      return EmailTemplateProviderBindingResponse.from(saved);
    } catch (RuntimeException exception) {
      binding.setStatus(EmailTemplateProviderBindingStatus.FAILED);
      binding.setErrorCode("MSG91_TEMPLATE_SYNC_FAILED");
      binding.setErrorMessage(safeMessage(exception));
      binding.setLastCheckedAt(Instant.now());
      bindingRepository.save(binding);
      log.warn(
          "msg91_template_sync_failed companyId={} templateId={} version={} connectionId={} reason={}",
          companyId,
          templateId,
          published.version(),
          connectionId,
          exception.getClass().getSimpleName());
      throw exception;
    }
  }

  public EmailTemplateProviderBindingResponse refresh(Long companyId, Long bindingId) {
    EmailTemplateProviderBinding binding =
        bindingRepository
            .findByIdAndCompanyId(bindingId, companyId)
            .orElseThrow(() -> new NotFoundException("Email template provider binding not found"));
    BroadcastSenderConnection connection =
        requireConnection(companyId, binding.getConnectionId(), binding.getProvider());
    requireUsableConnection(connection);
    return refreshInternal(binding, connection);
  }

  @Transactional(readOnly = true)
  public EmailTemplateProviderBinding requireSendableForBroadcast(
      Long companyId,
      Long templateId,
      long templateVersion,
      Long connectionId) {
    requireTemplate(companyId, templateId);
    BroadcastSenderConnection connection =
        requireConnection(companyId, connectionId, BroadcastSenderProvider.MSG91_EMAIL);
    requireUsableConnection(connection);
    EmailTemplateVersion version =
        versionRepository
            .findByTemplateIdAndVersionNumber(templateId, templateVersion)
            .orElseThrow(() -> new BadRequestException("Broadcast email template version no longer exists"));
    if (version.getPurpose() != EmailTemplatePurpose.TRANSACTIONAL) {
      throw new BadRequestException("MSG91 Email supports transactional templates only");
    }
    EmailTemplateProviderBinding binding =
        bindingRepository
            .findByTemplateVersionIdAndConnectionIdAndProvider(
                version.getId(), connectionId, BroadcastSenderProvider.MSG91_EMAIL)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "This published Email template is not synced to the selected MSG91 connection"));
    if (!companyId.equals(binding.getCompanyId())) {
      throw new BadRequestException("MSG91 template binding does not belong to this company");
    }
    if (!templateId.equals(binding.getTemplateId())
        || binding.getTemplateVersionNumber() != version.getVersionNumber()) {
      throw new BadRequestException("MSG91 template binding does not match the requested template version");
    }
    if (!java.util.Objects.equals(binding.getContentHash(), version.getContentHash())) {
      throw new BadRequestException("MSG91 template binding is stale for this template version");
    }
    if (!binding.getStatus().sendable()
        || binding.getProviderTemplateId() == null
        || binding.getProviderTemplateId().isBlank()) {
      throw new BadRequestException(
          "MSG91 template is not ready for sending. Current status: " + binding.getStatus());
    }
    return binding;
  }

  private EmailTemplateProviderBindingResponse refreshInternal(
      EmailTemplateProviderBinding binding, BroadcastSenderConnection connection) {
    try {
      Msg91EmailTemplateSnapshot snapshot =
          msg91EmailGateway.findTemplate(
              connection, binding.getProviderTemplateId(), binding.getProviderTemplateSlug());
      if (snapshot == null) {
        binding.setStatus(EmailTemplateProviderBindingStatus.SYNCED);
        binding.setProviderStatus("NOT_FOUND_IN_CURRENT_LIST");
        binding.setErrorCode("MSG91_TEMPLATE_STATUS_UNKNOWN");
        binding.setErrorMessage(
            "MSG91 did not return this template in the current template listing, so sending has been disabled until readiness can be confirmed.");
      } else {
        applySnapshot(binding, snapshot);
        binding.setErrorCode(null);
        binding.setErrorMessage(null);
      }
      binding.setLastCheckedAt(Instant.now());
      EmailTemplateProviderBinding saved = bindingRepository.save(binding);
      log.info(
          "msg91_template_status_refreshed companyId={} templateId={} version={} connectionId={} status={} providerStatus={}",
          saved.getCompanyId(),
          saved.getTemplateId(),
          saved.getTemplateVersionNumber(),
          saved.getConnectionId(),
          saved.getStatus(),
          saved.getProviderStatus());
      return EmailTemplateProviderBindingResponse.from(saved);
    } catch (RuntimeException exception) {
      binding.setErrorCode("MSG91_TEMPLATE_STATUS_FAILED");
      binding.setErrorMessage(safeMessage(exception));
      binding.setLastCheckedAt(Instant.now());
      bindingRepository.save(binding);
      log.warn(
          "msg91_template_status_refresh_failed companyId={} templateId={} version={} connectionId={} reason={}",
          binding.getCompanyId(),
          binding.getTemplateId(),
          binding.getTemplateVersionNumber(),
          binding.getConnectionId(),
          exception.getClass().getSimpleName());
      throw exception;
    }
  }

  private EmailTemplateProviderBinding newBinding(
      Long companyId,
      Long templateId,
      Long versionId,
      long versionNumber,
      Long connectionId,
      String slug,
      EmailTemplateService.PublishedEmail published) {
    EmailTemplateProviderBinding binding = new EmailTemplateProviderBinding();
    binding.setCompanyId(companyId);
    binding.setTemplateId(templateId);
    binding.setTemplateVersionId(versionId);
    binding.setTemplateVersionNumber(versionNumber);
    binding.setConnectionId(connectionId);
    binding.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    binding.setProviderTemplateSlug(slug);
    binding.setContentHash(published.contentHash());
    binding.setStatus(EmailTemplateProviderBindingStatus.SYNCING);
    return binding;
  }

  private EmailTemplateProviderBinding saveBinding(EmailTemplateProviderBinding binding) {
    try {
      return bindingRepository.saveAndFlush(binding);
    } catch (DataIntegrityViolationException exception) {
      return bindingRepository
          .findByTemplateVersionIdAndConnectionIdAndProvider(
              binding.getTemplateVersionId(),
              binding.getConnectionId(),
              binding.getProvider())
          .orElseThrow(() -> exception);
    }
  }

  private void applySnapshot(
      EmailTemplateProviderBinding binding, Msg91EmailTemplateSnapshot snapshot) {
    binding.setProviderTemplateId(snapshot.providerTemplateId());
    binding.setProviderTemplateSlug(snapshot.slug());
    binding.setProviderStatus(snapshot.providerStatus());
    binding.setStatus(snapshot.status());
  }

  private EmailTemplate requireTemplate(Long companyId, Long templateId) {
    return templateRepository
        .findByIdAndCompanyId(templateId, companyId)
        .orElseThrow(() -> new NotFoundException("Email template not found"));
  }

  private BroadcastSenderConnection requireConnection(
      Long companyId, Long connectionId, BroadcastSenderProvider provider) {
    BroadcastSenderConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast sender connection not found"));
    if (connection.getProvider() != provider) {
      throw new BadRequestException("Selected connection is not an MSG91 Email connection");
    }
    return connection;
  }

  private void requireUsableConnection(BroadcastSenderConnection connection) {
    if (connection.getStatus() != BroadcastSenderConnectionStatus.ACTIVE
        || !connection.isSendingEnabled()) {
      throw new BadRequestException("MSG91 Email connection is not active");
    }
  }

  private String providerSlug(Long templateId, long version, String contentHash) {
    String suffix =
        contentHash == null || contentHash.length() < 8
            ? "content"
            : contentHash.substring(0, 8).toLowerCase();
    return "messaging-" + templateId + "-v" + version + "-" + suffix;
  }

  private String msg91BroadcastHtml(String html) {
    if (html.contains("{{unsubscribe_url}}")) return html;
    return html
        + "<div data-unifydesk-unsubscribe=\"1\" style=\"margin-top:24px;font-size:12px\">"
        + "<a href=\"{{unsubscribe_url}}\">Unsubscribe from marketing emails</a></div>";
  }

  private String providerName(String templateName, long version) {
    String value = "Messaging - " + templateName + " - v" + version;
    return value.length() <= 160 ? value : value.substring(0, 160);
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}
