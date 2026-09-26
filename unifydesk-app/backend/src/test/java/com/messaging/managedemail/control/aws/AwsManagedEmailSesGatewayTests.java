package com.messaging.managedemail.control.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.managedemail.control.config.ManagedEmailProperties;
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
import software.amazon.awssdk.services.sesv2.model.GetReputationEntityRequest;
import software.amazon.awssdk.services.sesv2.model.GetTenantRequest;
import software.amazon.awssdk.services.sesv2.model.GetTenantResponse;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.PutTenantSuppressionAttributesRequest;
import software.amazon.awssdk.services.sesv2.model.PutTenantSuppressionAttributesResponse;
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
    ManagedEmailProperties properties = new ManagedEmailProperties();
    properties.setAwsRegion("ap-south-1");
    gateway = new AwsManagedEmailSesGateway(ses, sts, properties);
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
