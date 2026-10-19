package com.messaging.billing.service;

import com.messaging.billing.dto.BillingUsageSummaryResponse;
import com.messaging.billing.entity.CompanyWallet;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingUsageService {

  private final CompanyMemberRepository memberRepository;
  private final ContactRepository contactRepository;
  private final EntitlementService entitlementService;
  private final WalletService walletService;
  private final JdbcTemplate jdbcTemplate;

  public BillingUsageSummaryResponse getUsageSummary(Long companyId) {
    long seatsUsed = 1;
    try {
      seatsUsed = memberRepository.countByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE);
    } catch (Exception e) {
      log.debug("Error counting active seats for company {}: {}", companyId, e.getMessage());
    }

    long seatsLimit = 5;
    try {
      seatsLimit = entitlementService.integerValue(companyId, "MAX_WORKSPACE_USERS");
    } catch (Exception ignored) {
    }

    long contactsUsed = 0;
    try {
      contactsUsed = contactRepository.countActive(companyId);
    } catch (Exception e) {
      log.debug("Error counting contacts for company {}: {}", companyId, e.getMessage());
    }

    long contactsLimit = 1000;
    try {
      contactsLimit = entitlementService.integerValue(companyId, "CONTACT_LIMIT");
    } catch (Exception ignored) {
    }

    long emailMonthlyLimit = 1000;
    try {
      emailMonthlyLimit =
          entitlementService.integerValue(companyId, "MANAGED_EMAIL_MESSAGES_MONTHLY");
    } catch (Exception ignored) {
    }

    long smsMonthlyLimit = 100;
    try {
      smsMonthlyLimit = entitlementService.integerValue(companyId, "MANAGED_SMS_SEGMENTS_MONTHLY");
    } catch (Exception ignored) {
    }

    long emailMonthlyUsed = 0;
    try {
      Long count =
          jdbcTemplate.queryForObject(
              """
              select coalesce(sum(quantity), 0) from usage_events
              where company_id = ? and (channel = 'EMAIL' or billing_sku like '%EMAIL%')
                and occurred_at >= date_trunc('month', now())
              """,
              Long.class, companyId);
      emailMonthlyUsed = count != null ? count : 0;
    } catch (Exception e) {
      log.warn("Error counting email usage for company {}: {}", companyId, e.getMessage());
    }

    long smsMonthlyUsed = 0;
    try {
      Long count =
          jdbcTemplate.queryForObject(
              """
              select coalesce(sum(quantity), 0) from usage_events
              where company_id = ? and (channel = 'SMS' or billing_sku like '%SMS%')
                and occurred_at >= date_trunc('month', now())
              """,
              Long.class, companyId);
      smsMonthlyUsed = count != null ? count : 0;
    } catch (Exception e) {
      log.warn("Error counting sms usage for company {}: {}", companyId, e.getMessage());
    }

    CompanyWallet wallet = walletService.getOrCreateWallet(companyId);

    return new BillingUsageSummaryResponse(
        seatsUsed,
        seatsLimit,
        contactsUsed,
        contactsLimit,
        emailMonthlyUsed,
        emailMonthlyLimit,
        smsMonthlyUsed,
        smsMonthlyLimit,
        wallet.getBalance(),
        wallet.getCurrency());
  }
}
