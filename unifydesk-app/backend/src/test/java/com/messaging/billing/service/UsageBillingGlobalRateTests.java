package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.dto.ResolvedPrice;
import com.messaging.billing.entity.UsageEvent;
import com.messaging.billing.enums.*;
import com.messaging.billing.repository.UsageEventRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UsageBillingGlobalRateTests {
  private static final Instant AT = Instant.parse("2026-10-10T00:00:00Z");

  @Test
  void outboundEmailUsesExactSixDecimalGlobalPriceAndPersistsSnapshot() {
    var setup = setup(new BigDecimal("0.012000"));
    UsageEvent event = setup.service().record(
        1L, ServiceOwnership.MANAGED, BillingSku.MANAGED_EMAIL_OUTBOUND,
        "MANAGED_EMAIL", "recipient-1", "ses-message-1", "email:1",
        ChannelType.EMAIL, ProviderType.AMAZON_SES, BillingCategory.EMAIL_OUTBOUND,
        CommunicationDirection.OUTBOUND, null, null, UsageUnit.MESSAGE, 3, AT);

    assertEquals(PricingSource.GLOBAL_RATE, event.getPricingSource());
    assertEquals(new BigDecimal("0.012000"), event.getUnitPriceSnapshot());
    assertEquals(new BigDecimal("0.036000"), event.getTotalPrice());
    assertEquals(900L, event.getPricingRuleIdSnapshot());
    assertNull(event.getRateCardIdSnapshot());
    assertEquals(3, event.getQuantity());
    verify(setup.events(), times(1)).saveAndFlush(any(UsageEvent.class));
  }

  @Test
  void smsSegmentsAreBilledOncePerSegmentAndDuplicateEventsDoNotDoubleCharge() {
    var setup = setup(new BigDecimal("0.250000"));
    when(setup.events().findByIdempotencyKey("sms:1"))
        .thenReturn(Optional.empty())
        .thenAnswer(invocation -> Optional.of(setup.lastSaved()[0]));
    UsageEvent first = setup.service().record(
        1L, ServiceOwnership.MANAGED, BillingSku.MANAGED_SMS_SEGMENT,
        "MANAGED_SMS", "recipient-2", "msg91-message-1", "sms:1",
        ChannelType.SMS, ProviderType.MSG91_SMS, BillingCategory.SMS_TRANSACTIONAL,
        CommunicationDirection.OUTBOUND, "IN", null, UsageUnit.SEGMENT, 3, AT);
    UsageEvent duplicate = setup.service().record(
        1L, ServiceOwnership.MANAGED, BillingSku.MANAGED_SMS_SEGMENT,
        "MANAGED_SMS", "recipient-2", "msg91-message-1", "sms:1",
        ChannelType.SMS, ProviderType.MSG91_SMS, BillingCategory.SMS_TRANSACTIONAL,
        CommunicationDirection.OUTBOUND, "IN", null, UsageUnit.SEGMENT, 3, AT);

    assertSame(first, duplicate);
    assertEquals(new BigDecimal("0.750000"), first.getTotalPrice());
    verify(setup.events(), times(1)).saveAndFlush(any(UsageEvent.class));
  }

  private Setup setup(BigDecimal unitPrice) {
    UsageEventRepository events = mock(UsageEventRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    PricingResolver pricing = mock(PricingResolver.class);
    BillingPeriodService periods = mock(BillingPeriodService.class);
    Company company = new Company();
    company.setId(1L);
    when(companies.findById(1L)).thenReturn(Optional.of(company));
    when(pricing.resolve(anyLong(), any(), any(), any(), any(),
        nullable(String.class), nullable(String.class), any(), eq(AT)))
        .thenReturn(new ResolvedPrice(unitPrice, "INR", PricingSource.GLOBAL_RATE,
            "900", "10", "20"));
    UsageEvent[] saved = new UsageEvent[1];
    when(events.saveAndFlush(any(UsageEvent.class))).thenAnswer(invocation -> {
      saved[0] = invocation.getArgument(0);
      return saved[0];
    });
    return new Setup(new UsageBillingService(events, companies, pricing, periods), events, saved);
  }

  private record Setup(
      UsageBillingService service, UsageEventRepository events, UsageEvent[] lastSaved) {}
}
