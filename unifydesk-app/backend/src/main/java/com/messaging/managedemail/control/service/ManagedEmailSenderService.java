package com.messaging.managedemail.control.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.*;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.exception.*;
import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.*;
import com.messaging.managedemail.control.entity.*;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import com.messaging.managedemail.control.repository.*;
import jakarta.mail.internet.InternetAddress;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailSenderService {

  private static final long CONNECTION_LOCK_NAMESPACE = 0x434f4e4e45435400L;

  private final ManagedEmailDomainRepository domainRepository;
  private final ManagedEmailTenantRepository tenantRepository;
  private final ManagedEmailSenderBindingRepository bindingRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final BroadcastSenderRepository senderRepository;
  private final ChannelConnectionRepository channelConnectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ManagedEmailEntitlementService entitlementService;
  private final ManagedEmailSenderLifecycleService lifecycleService;
  private final ManagedEmailProperties properties;
  private final JdbcTemplate jdbcTemplate;

  @Transactional(readOnly = true)
  public List<SenderResponse> list(Long companyId) {
    return bindingRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(binding -> response(companyId, binding))
        .toList();
  }

  @Transactional
  public SenderResponse create(Long companyId, Long userId, CreateSenderRequest request) {
    entitlementService.requireEnabled(companyId);
    entitlementService.requireSenderCapacity(companyId);
    Long domainId = parseId(request.domainId(), "domainId");
    ManagedEmailDomain domain =
        domainRepository
            .findByIdAndCompanyId(domainId, companyId)
            .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
    if (domain.getStatus() != DomainStatus.ACTIVE) {
      throw new BadRequestException("Managed Email domain is not active");
    }
    ManagedEmailTenant tenant =
        tenantRepository
            .findByIdAndCompanyId(domain.getTenantId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed Email tenant not found"));
    if (tenant.getStatus() != TenantStatus.ACTIVE
        || tenant.getSendingStatus() != SendingStatus.ENABLED) {
      throw new BadRequestException("Managed Email sending is not available");
    }

    String localPart = normalizeLocalPart(request.localPart());
    String fromAddress = localPart + "@" + domain.getNormalizedDomain();
    String replyTo = normalizeOptionalEmail(request.replyToAddress(), "Reply-To address");

    if (senderRepository
        .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            companyId, BroadcastSenderProvider.AMAZON_SES, fromAddress)
        .isPresent()) {
      throw new ConflictException("This sender address is already connected");
    }

    advisoryLock(CONNECTION_LOCK_NAMESPACE ^ companyId);
    BroadcastSenderConnection connection = ensureConnection(companyId, userId, tenant);

    BroadcastSender sender = new BroadcastSender();
    sender.setId(IdGenerator.nextId());
    sender.setCompanyId(companyId);
    sender.setConnectionId(connection.getId());
    sender.setProvider(BroadcastSenderProvider.AMAZON_SES);
    sender.setFromAddress(fromAddress);
    sender.setDisplayName(trimToNull(request.displayName()));
    sender.setReplyToAddress(replyTo);
    sender.setVerifiedIdentity(domain.getNormalizedDomain());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    BroadcastSender savedSender = senderRepository.save(sender);

    ChannelConnection channelConnection =
        channelConnectionRepository
            .findByIdAndCompanyId(connection.getChannelConnectionId(), companyId)
            .orElseThrow(() -> new IllegalStateException("Managed channel connection missing"));
    SenderIdentity identity = new SenderIdentity();
    identity.setId(savedSender.getId());
    identity.setConnection(channelConnection);
    identity.setIdentityType(SenderIdentityType.EMAIL_ADDRESS);
    identity.setValue(savedSender.getFromAddress());
    identity.setExternalId(savedSender.getId().toString());
    identity.setStatus(SenderIdentityStatus.ACTIVE);
    identity.setDefaultIdentity(
        senderIdentityRepository.countByConnectionCompanyIdAndIdentityType(
                companyId, SenderIdentityType.EMAIL_ADDRESS)
            == 0);
    senderIdentityRepository.save(identity);

    ManagedEmailSenderBinding binding = new ManagedEmailSenderBinding();
    binding.setSenderId(savedSender.getId());
    binding.setCompanyId(companyId);
    binding.setDomainId(domain.getId());
    binding.setTenantId(tenant.getId());
    bindingRepository.save(binding);

    return response(savedSender, connection, domain);
  }

  @Transactional
  public SenderResponse disable(Long companyId, Long senderId) {
    ManagedEmailSenderBinding binding =
        bindingRepository
            .findBySenderIdAndCompanyId(senderId, companyId)
            .orElseThrow(() -> new NotFoundException("Managed Email sender not found"));
    lifecycleService.disableSender(companyId, senderId);
    return response(companyId, binding);
  }

  private BroadcastSenderConnection ensureConnection(
      Long companyId, Long userId, ManagedEmailTenant tenant) {
    var existing =
        connectionRepository
            .findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
                companyId,
                BroadcastSenderProvider.AMAZON_SES,
                BroadcastSenderManagementMode.MESSAGING_MANAGED,
                BroadcastSenderConnectionStatus.DISABLED);
    if (existing.isPresent()) return existing.get();

    Long id = IdGenerator.nextId();
    ChannelConnection channelConnection = new ChannelConnection();
    channelConnection.setId(id);
    channelConnection.setCompanyId(companyId);
    channelConnection.setChannelType(ChannelType.EMAIL);
    channelConnection.setProviderType(ProviderType.AMAZON_SES);
    channelConnection.setName("UnifyDesk Managed Email");
    channelConnection.setExternalAccountId("managed-email-" + id);
    channelConnection.setStatus(ChannelConnectionStatus.ACTIVE);
    channelConnection.setCreatedByUserId(userId);
    channelConnection.setLastSyncAt(tenant.getLastProviderSyncAt());
    channelConnectionRepository.save(channelConnection);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(id);
    connection.setChannelConnectionId(id);
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setAuthMode(BroadcastSenderAuthMode.IAM_ROLE);
    connection.setName("UnifyDesk Managed Email");
    connection.setRegion(properties.getAwsRegion());
    connection.setRoleArn(null);
    connection.setExternalId(null);
    connection.setEncryptedCredentials(null);
    connection.setProviderConfigJson(null);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setProductionAccessEnabled(true);
    connection.setSendingEnabled(
        properties.isEnabled()
            && properties.isPlatformSendingEnabled()
            && tenant.getStatus() == TenantStatus.ACTIVE
            && tenant.getSendingStatus() == SendingStatus.ENABLED);
    connection.setLastValidatedAt(tenant.getLastProviderSyncAt());
    BroadcastSenderConnection saved = connectionRepository.save(connection);

    tenant.setBroadcastConnectionId(saved.getId());
    tenantRepository.save(tenant);
    return saved;
  }

  private SenderResponse response(Long companyId, ManagedEmailSenderBinding binding) {
    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(binding.getSenderId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed Email sender not found"));
    BroadcastSenderConnection connection =
        connectionRepository
            .findByIdAndCompanyId(sender.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed Email connection not found"));
    ManagedEmailDomain domain =
        domainRepository
            .findByIdAndCompanyId(binding.getDomainId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
    return response(sender, connection, domain);
  }

  private SenderResponse response(
      BroadcastSender sender, BroadcastSenderConnection connection, ManagedEmailDomain domain) {
    return new SenderResponse(
        sender.getId().toString(),
        connection.getId().toString(),
        connection.getChannelConnectionId().toString(),
        sender.getProvider(),
        connection.getManagementMode(),
        sender.getStatus(),
        sender.getFromAddress(),
        sender.getDisplayName(),
        sender.getReplyToAddress(),
        "UnifyDesk Managed Email",
        "MANAGED",
        connection.isProductionAccessEnabled(),
        connection.isSendingEnabled() && sender.getStatus() == BroadcastSenderStatus.ACTIVE,
        null,
        null,
        null,
        connection.getLastValidatedAt(),
        domain.getId().toString(),
        domain.getDisplayDomain());
  }

  private String normalizeLocalPart(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Sender local part is required");
    }
    String local = value.trim().toLowerCase(Locale.ROOT);
    if (local.length() > 64
        || local.contains("@")
        || local.startsWith(".")
        || local.endsWith(".")
        || local.contains("..")
        || local.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch))) {
      throw new BadRequestException("Sender local part is invalid");
    }
    try {
      InternetAddress address = new InternetAddress(local + "@example.com", true);
      address.validate();
      if (!address.getAddress().equals(local + "@example.com")) {
        throw new IllegalArgumentException();
      }
      return local;
    } catch (Exception exception) {
      throw new BadRequestException("Sender local part is invalid");
    }
  }

  private String normalizeOptionalEmail(String value, String label) {
    if (value == null || value.isBlank()) return null;
    try {
      InternetAddress address = new InternetAddress(value.trim(), true);
      address.validate();
      return address.getAddress().trim().toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      throw new BadRequestException(label + " is invalid");
    }
  }

  private Long parseId(String value, String field) {
    try {
      return Long.valueOf(value);
    } catch (Exception exception) {
      throw new BadRequestException(field + " is invalid");
    }
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void advisoryLock(long key) {
    jdbcTemplate.queryForObject("select pg_advisory_xact_lock(?)::text", String.class, key);
  }
}
