package com.messaging.managedemail.control.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.managedemail.control.config.ManagedEmailProperties;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.CreateTenantRequest;
import software.amazon.awssdk.services.sesv2.model.CreateTenantResponse;
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest;
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse;
import software.amazon.awssdk.services.sesv2.model.GetReputationEntityRequest;
import software.amazon.awssdk.services.sesv2.model.GetTenantRequest;
import software.amazon.awssdk.services.sesv2.model.GetTenantResponse;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.PutTenantSuppressionAttributesRequest;
import software.amazon.awssdk.services.sesv2.model.PutTenantSuppressionAttributesResponse;
import software.amazon.awssdk.services.sesv2.model.SendQuota;
import software.amazon.awssdk.services.sesv2.model.SendingStatus;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;
import software.amazon.awssdk.services.sesv2.model.SuppressionListScope;
import software.amazon.awssdk.services.sesv2.model.Tenant;
import software.amazon.awssdk.services.sts.StsClient;

@ExtendWith(MockitoExtension.class)
class AwsManagedEmailSesGatewayTests {

  @Mock private SesV2Client ses;
  @Mock private StsClient sts;

  private AwsManagedEmailSesGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new AwsManagedEmailSesGateway(ses, sts, properties());
  }

  private ManagedEmailProperties properties() {
    ManagedEmailProperties properties = new ManagedEmailProperties();
    properties.setAwsRegion("ap-south-1");
    return properties;
  }

  @Test
  void createsTenantThenAppliesTenantScopedBounceAndComplaintSuppression() {
    when(ses.getTenant(any(GetTenantRequest.class)))
        .thenThrow(NotFoundException.builder().message("missing").build())
        .thenReturn(enabledTenant());
    when(ses.createTenant(any(CreateTenantRequest.class)))
        .thenReturn(CreateTenantResponse.builder().build());
    when(ses.putTenantSuppressionAttributes(any(PutTenantSuppressionAttributesRequest.class)))
        .thenReturn(PutTenantSuppressionAttributesResponse.builder().build());
    when(ses.getReputationEntity(any(GetReputationEntityRequest.class)))
        .thenThrow(NotFoundException.builder().message("not ready").build());

    var snapshot = gateway.ensureTenant("ud-c-opaque");

    assertThat(snapshot.sendingStatus()).isEqualTo("ENABLED");

    ArgumentCaptor<CreateTenantRequest> createCaptor =
        ArgumentCaptor.forClass(CreateTenantRequest.class);
    verify(ses).createTenant(createCaptor.capture());
    assertThat(createCaptor.getValue().tenantName()).isEqualTo("ud-c-opaque");

    ArgumentCaptor<PutTenantSuppressionAttributesRequest> suppressionCaptor =
        ArgumentCaptor.forClass(PutTenantSuppressionAttributesRequest.class);
    verify(ses).putTenantSuppressionAttributes(suppressionCaptor.capture());
    assertThat(suppressionCaptor.getValue().suppressionScope())
        .isEqualTo(SuppressionListScope.TENANT);
    assertThat(suppressionCaptor.getValue().suppressedReasons())
        .containsExactlyInAnyOrder(SuppressionListReason.BOUNCE, SuppressionListReason.COMPLAINT);
  }


  @Test
  void readsCompleteSesAccountStateAndRollingQuotaSnapshot() {
    when(ses.getTenant(any(GetTenantRequest.class))).thenReturn(enabledTenant());
    when(ses.getReputationEntity(any(GetReputationEntityRequest.class)))
        .thenThrow(NotFoundException.builder().message("not ready").build());
    when(ses.getAccount(any(GetAccountRequest.class)))
        .thenReturn(
            GetAccountResponse.builder()
                .sendingEnabled(true)
                .productionAccessEnabled(true)
                .sendQuota(
                    SendQuota.builder()
                        .maxSendRate(25.0)
                        .max24HourSend(50000.0)
                        .sentLast24Hours(1234.0)
                        .build())
                .build());

    var snapshot = gateway.getTenant("ud-c-opaque");

    assertThat(snapshot.maxSendRate()).isEqualTo(25.0);
    assertThat(snapshot.max24HourSend()).isEqualTo(50000.0);
    assertThat(snapshot.sentLast24Hours()).isEqualTo(1234.0);
    assertThat(snapshot.accountSendingEnabled()).isTrue();
    assertThat(snapshot.productionAccessEnabled()).isTrue();
  }


  @Test
  void providerObservationTimestampIsCapturedBeforeGetAccountAndMatchesQuotaRead() {
    Instant observedAt = Instant.parse("2026-09-26T10:00:00Z");
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(observedAt);
    gateway = new AwsManagedEmailSesGateway(ses, sts, properties(), clock);

    when(ses.getTenant(any(GetTenantRequest.class))).thenReturn(enabledTenant());
    when(ses.getReputationEntity(any(GetReputationEntityRequest.class)))
        .thenThrow(NotFoundException.builder().message("not ready").build());
    when(ses.getAccount(any(GetAccountRequest.class)))
        .thenReturn(
            GetAccountResponse.builder()
                .sendingEnabled(true)
                .productionAccessEnabled(true)
                .sendQuota(
                    SendQuota.builder()
                        .maxSendRate(30.0)
                        .max24HourSend(60000.0)
                        .sentLast24Hours(4321.0)
                        .build())
                .build());

    var snapshot = gateway.getTenant("ud-c-opaque");

    assertThat(snapshot.providerObservedAt()).isEqualTo(observedAt);
    assertThat(snapshot.maxSendRate()).isEqualTo(30.0);
    assertThat(snapshot.max24HourSend()).isEqualTo(60000.0);
    assertThat(snapshot.sentLast24Hours()).isEqualTo(4321.0);
    var order = inOrder(clock, ses);
    order.verify(clock).instant();
    order.verify(ses).getAccount(any(GetAccountRequest.class));
  }

  @Test
  void eachNewAwsAccountReadReceivesANewObservationTimestamp() {
    Instant firstObservedAt = Instant.parse("2026-09-26T10:00:00Z");
    Instant secondObservedAt = Instant.parse("2026-09-26T10:05:00Z");
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(firstObservedAt, secondObservedAt);
    gateway = new AwsManagedEmailSesGateway(ses, sts, properties(), clock);

    when(ses.getTenant(any(GetTenantRequest.class))).thenReturn(enabledTenant());
    when(ses.getReputationEntity(any(GetReputationEntityRequest.class)))
        .thenThrow(NotFoundException.builder().message("not ready").build());
    when(ses.getAccount(any(GetAccountRequest.class)))
        .thenReturn(
            GetAccountResponse.builder()
                .sendingEnabled(true)
                .productionAccessEnabled(true)
                .sendQuota(
                    SendQuota.builder()
                        .maxSendRate(20.0)
                        .max24HourSend(40000.0)
                        .sentLast24Hours(1000.0)
                        .build())
                .build(),
            GetAccountResponse.builder()
                .sendingEnabled(true)
                .productionAccessEnabled(true)
                .sendQuota(
                    SendQuota.builder()
                        .maxSendRate(10.0)
                        .max24HourSend(20000.0)
                        .sentLast24Hours(5000.0)
                        .build())
                .build());

    var first = gateway.getTenant("ud-c-opaque");
    var second = gateway.getTenant("ud-c-opaque");

    assertThat(first.providerObservedAt()).isEqualTo(firstObservedAt);
    assertThat(first.maxSendRate()).isEqualTo(20.0);
    assertThat(first.max24HourSend()).isEqualTo(40000.0);
    assertThat(first.sentLast24Hours()).isEqualTo(1000.0);
    assertThat(second.providerObservedAt()).isEqualTo(secondObservedAt);
    assertThat(second.maxSendRate()).isEqualTo(10.0);
    assertThat(second.max24HourSend()).isEqualTo(20000.0);
    assertThat(second.sentLast24Hours()).isEqualTo(5000.0);
  }

  @Test
  void ambiguousCreateConvergesWhenTenantAlreadyExists() {
    when(ses.getTenant(any(GetTenantRequest.class)))
        .thenThrow(NotFoundException.builder().message("missing").build())
        .thenReturn(enabledTenant());
    when(ses.createTenant(any(CreateTenantRequest.class)))
        .thenThrow(AlreadyExistsException.builder().message("already exists").build());
    when(ses.putTenantSuppressionAttributes(any(PutTenantSuppressionAttributesRequest.class)))
        .thenReturn(PutTenantSuppressionAttributesResponse.builder().build());
    when(ses.getReputationEntity(any(GetReputationEntityRequest.class)))
        .thenThrow(NotFoundException.builder().message("not ready").build());

    var snapshot = gateway.ensureTenant("ud-c-opaque");

    assertThat(snapshot.sendingStatus()).isEqualTo("ENABLED");
    verify(sts, never()).getCallerIdentity();
  }

  private GetTenantResponse enabledTenant() {
    return GetTenantResponse.builder()
        .tenant(
            Tenant.builder()
                .tenantName("ud-c-opaque")
                .tenantArn("arn:aws:ses:ap-south-1:123456789012:tenant/ud-c-opaque")
                .sendingStatus(SendingStatus.ENABLED)
                .build())
        .build();
  }
}
