package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.enums.*;
import com.messaging.billing.repository.UsageEventRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.company.repository.CompanyRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UsageBillingOwnershipTests {

  @Test
  void customerOwnedEmailIsNeverFinanciallyRated() {
    UsageEventRepository events = mock(UsageEventRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    PricingResolver pricing = mock(PricingResolver.class);
    BillingPeriodService periods = mock(BillingPeriodService.class);
    UsageBillingService service = new UsageBillingService(events, companies, pricing, periods);

    var result =
        service.record(
            1L,
            ServiceOwnership.CUSTOMER_OWNED,
            null,
            "DIRECT_EMAIL",
            "recipient-1",
            "provider-1",
            "direct:1",
            ChannelType.EMAIL,
            ProviderType.SENDGRID,
            BillingCategory.EMAIL_OUTBOUND,
            CommunicationDirection.OUTBOUND,
            null,
            null,
            UsageUnit.MESSAGE,
            1,
            Instant.parse("2026-10-01T00:00:00Z"));

    assertNull(result);
    verifyNoInteractions(events, companies, pricing, periods);
  }

  @Test
  void customerOwnedSmsIsNeverFinanciallyRated() {
    UsageEventRepository events = mock(UsageEventRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    PricingResolver pricing = mock(PricingResolver.class);
    BillingPeriodService periods = mock(BillingPeriodService.class);
    UsageBillingService service = new UsageBillingService(events, companies, pricing, periods);

    var result =
        service.record(
            1L,
            ServiceOwnership.CUSTOMER_OWNED,
            null,
            "DIRECT_SMS",
            "recipient-2",
            "provider-2",
            "direct:2",
            ChannelType.SMS,
            ProviderType.MSG91_SMS,
            BillingCategory.SMS_TRANSACTIONAL,
            CommunicationDirection.OUTBOUND,
            "IN",
            null,
            UsageUnit.SEGMENT,
            1,
            Instant.parse("2026-10-01T00:00:00Z"));

    assertNull(result);
    verifyNoInteractions(events, companies, pricing, periods);
  }
}
