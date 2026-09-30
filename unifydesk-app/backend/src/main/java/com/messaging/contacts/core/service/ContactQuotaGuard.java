package com.messaging.contacts.core.service;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactQuotaGuard {

  private static final long LOCK_NAMESPACE_CONTACT = 0x434f4e54414354L;
  private static final long LOCK_NAMESPACE_FIELD = 0x4649454c44L;
  private static final long LOCK_NAMESPACE_LIST = 0x4c495354L;

  private final JdbcTemplate jdbcTemplate;
  private final EntitlementService entitlementService;
  private final ContactRepository contactRepository;
  private final CustomFieldRepository customFieldRepository;
  private final ContactListRepository listRepository;

  public void requireContactCapacity(long companyId) {
    requireContactCapacity(companyId, 1);
  }

  public void requireContactCapacity(long companyId, long additionalContacts) {
    if (additionalContacts <= 0) return;
    lock(companyId, LOCK_NAMESPACE_CONTACT);
    try {
      long limit = entitlementService.integerValue(companyId, "CONTACT_LIMIT");
      long current = contactRepository.countActive(companyId);
      if (limit >= 0 && (additionalContacts > limit || current > limit - additionalContacts)) {
        throw ContactCoreException.contactLimit();
      }
    } catch (PlanEntitlementException exception) {
      throw ContactCoreException.contactLimit();
    }
  }

  public void requireCustomFieldCapacity(long companyId) {
    lock(companyId, LOCK_NAMESPACE_FIELD);
    try {
      entitlementService.assertWithinLimit(
          companyId,
          "CONTACT_CUSTOM_FIELD_LIMIT",
          customFieldRepository.countActiveDefinitions(companyId));
    } catch (PlanEntitlementException exception) {
      throw ContactCoreException.fieldLimit();
    }
  }

  public void requireListCapacity(long companyId) {
    lock(companyId, LOCK_NAMESPACE_LIST);
    try {
      entitlementService.assertWithinLimit(
          companyId, "CONTACT_LIST_LIMIT", listRepository.countActive(companyId));
    } catch (PlanEntitlementException exception) {
      throw ContactCoreException.listLimit();
    }
  }

  private void lock(long companyId, long namespace) {
    long key = namespace ^ Long.rotateLeft(companyId, 17);
    jdbcTemplate.query("select pg_advisory_xact_lock(?)", rs -> null, key);
  }
}
