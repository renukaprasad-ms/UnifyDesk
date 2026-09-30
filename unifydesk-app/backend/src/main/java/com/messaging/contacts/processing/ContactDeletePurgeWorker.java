package com.messaging.contacts.processing;

import com.messaging.contacts.consent.ContactSuppressionRepository;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.SuppressionState;
import com.messaging.contacts.consent.SuppressionFingerprintService;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.domain.CoreRows.IdentityRow;
import com.messaging.contacts.core.repository.ContactRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactDeletePurgeWorker {

  private final ContactRepository contactRepository;
  private final ContactSuppressionRepository suppressionRepository;
  private final SuppressionFingerprintService fingerprintService;

  @Transactional
  public void process(long companyId, long contactId) {
    List<IdentityRow> identities = contactRepository.identities(companyId, contactId);

    suppressionRepository.list(companyId, contactId).stream()
        .filter(item -> item.state() == SuppressionState.ACTIVE)
        .forEach(
            suppression -> {
              List<IdentityRow> protectedIdentities =
                  relevantIdentities(identities, suppression.identityId(), suppression.channel());
              if (protectedIdentities.isEmpty()) return;

              IdentityRow first = protectedIdentities.getFirst();
              suppressionRepository.tombstoneIdentity(
                  companyId,
                  suppression.id(),
                  fingerprintService.fingerprint(
                      suppression.channel(), first.normalizedValue()));

              for (int index = 1; index < protectedIdentities.size(); index++) {
                IdentityRow identity = protectedIdentities.get(index);
                suppressionRepository.copyTombstone(
                    companyId,
                    suppression.id(),
                    fingerprintService.fingerprint(
                        suppression.channel(), identity.normalizedValue()));
              }
            });

    contactRepository.purgeProfile(companyId, contactId);
  }

  private List<IdentityRow> relevantIdentities(
      List<IdentityRow> identities, Long identityId, Channel channel) {
    if (identityId != null) {
      return identities.stream()
          .filter(identity -> identity.id() == identityId)
          .toList();
    }
    IdentityType expected = channel == Channel.EMAIL ? IdentityType.EMAIL : IdentityType.PHONE;
    return identities.stream()
        .filter(identity -> identity.type() == expected)
        .toList();
  }
}
