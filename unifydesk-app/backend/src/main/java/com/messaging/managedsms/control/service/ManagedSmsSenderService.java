package com.messaging.managedsms.control.service;

import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.entity.*;
import com.messaging.channel.enums.*;
import com.messaging.channel.repository.*;
import com.messaging.common.exception.*;
import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.CreateSenderRequest;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsSenderService {

  private final ManagedSmsHeaderService headerService;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsSenderBindingRepository bindingRepository;
  private final BroadcastSenderRepository senderRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final ChannelConnectionRepository channelConnectionRepository;
  private final SenderIdentityRepository identityRepository;

  @Transactional(readOnly = true)
  public List<BroadcastSenderResponse> list(Long companyId) {
    return bindingRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(binding -> response(companyId, binding))
        .toList();
  }

  @Transactional
  public BroadcastSenderResponse create(
      Long companyId, Long userId, CreateSenderRequest request) {
    Long headerId = parseId(request.headerId());
    ManagedSmsHeader header = headerService.require(companyId, headerId);
    if (header.getStatus() != HeaderStatus.ACTIVE
        || header.getProviderMappingStatus() != ProviderMappingStatus.READY) {
      throw new BadRequestException("MANAGED_SMS_HEADER_NOT_READY: Header is not ready");
    }
    ManagedSmsTenant tenant =
        tenantRepository
            .findByCompanyId(companyId)
            .filter(item -> item.getStatus() == TenantStatus.READY)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "MANAGED_SMS_NOT_READY: Managed SMS setup is not ready"));
    var existingBinding = bindingRepository.findByCompanyIdAndHeaderId(companyId, headerId);
    if (existingBinding.isPresent()) return response(companyId, existingBinding.get());

    BroadcastSenderConnection connection =
        connectionRepository
            .findByIdAndCompanyId(tenant.getBroadcastConnectionId(), companyId)
            .filter(
                item ->
                    item.getProvider() == BroadcastSenderProvider.MSG91_SMS
                        && item.getManagementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED
                        && item.getEncryptedCredentials() == null)
            .orElseThrow(() -> new IllegalStateException("Managed SMS connection is invalid"));

    BroadcastSender sender = new BroadcastSender();
    sender.setId(IdGenerator.nextId());
    sender.setCompanyId(companyId);
    sender.setConnectionId(connection.getId());
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);
    sender.setFromAddress(header.getHeader());
    sender.setDisplayName(
        request.displayName() == null || request.displayName().isBlank()
            ? header.getHeader()
            : request.displayName().trim());
    sender.setReplyToAddress(null);
    sender.setVerifiedIdentity(header.getHeader());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    BroadcastSender saved = senderRepository.save(sender);

    ChannelConnection channel =
        channelConnectionRepository
            .findByIdAndCompanyId(connection.getChannelConnectionId(), companyId)
            .orElseThrow(() -> new IllegalStateException("Managed SMS channel connection missing"));
    SenderIdentity identity = new SenderIdentity();
    identity.setId(saved.getId());
    identity.setConnection(channel);
    identity.setIdentityType(SenderIdentityType.SMS_HEADER);
    identity.setValue(header.getHeader());
    identity.setExternalId(saved.getId().toString());
    identity.setStatus(SenderIdentityStatus.ACTIVE);
    identity.setDefaultIdentity(
        identityRepository
            .findFirstByConnectionIdAndDefaultIdentityTrue(channel.getId())
            .isEmpty());
    identityRepository.save(identity);

    ManagedSmsSenderBinding binding = new ManagedSmsSenderBinding();
    binding.setSenderId(saved.getId());
    binding.setCompanyId(companyId);
    binding.setTenantId(tenant.getId());
    binding.setHeaderId(header.getId());
    bindingRepository.save(binding);
    return BroadcastSenderResponse.from(saved, connection);
  }

  private BroadcastSenderResponse response(Long companyId, ManagedSmsSenderBinding binding) {
    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(binding.getSenderId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed SMS sender not found"));
    BroadcastSenderConnection connection =
        connectionRepository
            .findByIdAndCompanyId(sender.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed SMS connection not found"));
    return BroadcastSenderResponse.from(sender, connection);
  }

  private Long parseId(String value) {
    try {
      return Long.valueOf(value);
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_HEADER_NOT_READY: Invalid headerId");
    }
  }
}
