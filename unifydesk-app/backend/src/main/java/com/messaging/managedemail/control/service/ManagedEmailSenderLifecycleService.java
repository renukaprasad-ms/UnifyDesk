package com.messaging.managedemail.control.service;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.managedemail.control.entity.ManagedEmailSenderBinding;
import com.messaging.managedemail.control.repository.ManagedEmailSenderBindingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailSenderLifecycleService {

  private final ManagedEmailSenderBindingRepository bindingRepository;
  private final BroadcastSenderRepository senderRepository;
  private final SenderIdentityRepository senderIdentityRepository;

  @Transactional
  public void setDomainSendersEnabled(Long companyId, Long domainId, boolean enabled) {
    List<ManagedEmailSenderBinding> bindings =
        bindingRepository.findByCompanyIdAndDomainId(companyId, domainId);
    for (ManagedEmailSenderBinding binding : bindings) {
      BroadcastSender sender =
          senderRepository.findByIdAndCompanyId(binding.getSenderId(), companyId).orElse(null);
      if (sender == null) continue;
      sender.setStatus(enabled ? BroadcastSenderStatus.ACTIVE : BroadcastSenderStatus.DISABLED);
      senderRepository.save(sender);
      senderIdentityRepository
          .findByIdAndConnectionId(sender.getId(), sender.getConnectionId())
          .ifPresent(
              identity -> {
                identity.setStatus(
                    enabled ? SenderIdentityStatus.ACTIVE : SenderIdentityStatus.DISABLED);
                senderIdentityRepository.save(identity);
              });
    }
  }

  @Transactional
  public void disableSender(Long companyId, Long senderId) {
    ManagedEmailSenderBinding binding =
        bindingRepository
            .findBySenderIdAndCompanyId(senderId, companyId)
            .orElseThrow(
                () ->
                    new com.messaging.common.exception.NotFoundException(
                        "Managed Email sender not found"));
    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(binding.getSenderId(), companyId)
            .orElseThrow(
                () ->
                    new com.messaging.common.exception.NotFoundException(
                        "Managed Email sender not found"));
    sender.setStatus(BroadcastSenderStatus.DISABLED);
    senderRepository.save(sender);
    senderIdentityRepository
        .findByIdAndConnectionId(sender.getId(), sender.getConnectionId())
        .ifPresent(
            identity -> {
              identity.setStatus(SenderIdentityStatus.DISABLED);
              senderIdentityRepository.save(identity);
            });
  }
}
