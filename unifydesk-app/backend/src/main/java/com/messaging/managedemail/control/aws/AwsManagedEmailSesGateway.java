package com.messaging.managedemail.control.aws;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;
import software.amazon.awssdk.services.sts.StsClient;

@Slf4j
@Component
public class AwsManagedEmailSesGateway implements ManagedEmailSesGateway {

  private static final String EVENT_DESTINATION_NAME = "unifydesk-managed-email";
  private static final List<String> EVENT_TYPES =
      List.of(
          "SEND",
          "DELIVERY",
          "BOUNCE",
          "COMPLAINT",
          "REJECT",
          "OPEN",
          "CLICK",
          "DELIVERY_DELAY",
          "SUBSCRIPTION",
          "RENDERING_FAILURE");

  private final SesV2Client ses;
  private final StsClient sts;
  private final ManagedEmailProperties properties;
  private final Clock clock;
  private final AtomicReference<String> accountId = new AtomicReference<>();

  @Autowired
  public AwsManagedEmailSesGateway(
      SesV2Client ses, StsClient sts, ManagedEmailProperties properties) {
    this(ses, sts, properties, Clock.systemUTC());
  }

  AwsManagedEmailSesGateway(
      SesV2Client ses, StsClient sts, ManagedEmailProperties properties, Clock clock) {
    this.ses = ses;
    this.sts = sts;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public TenantSnapshot ensureTenant(String tenantName) {
    try {
      try {
        ses.getTenant(GetTenantRequest.builder().tenantName(tenantName).build());
      } catch (software.amazon.awssdk.services.sesv2.model.NotFoundException exception) {
        try {
          ses.createTenant(CreateTenantRequest.builder().tenantName(tenantName).build());
        } catch (AlreadyExistsException ignored) {
          // Ambiguous create/retry converges through GetTenant below.
        } catch (Exception ex) {
          log.warn("Multi-tenant SES createTenant not supported or failed: {}", ex.getMessage());
        }
      } catch (Exception ex) {
        log.warn("Multi-tenant SES getTenant not supported or failed: {}", ex.getMessage());
      }

      try {
        ses.putTenantSuppressionAttributes(
            PutTenantSuppressionAttributesRequest.builder()
                .tenantName(tenantName)
                .suppressionScope(SuppressionListScope.TENANT)
                .suppressedReasons(SuppressionListReason.BOUNCE, SuppressionListReason.COMPLAINT)
                .build());
      } catch (Exception ex) {
        log.warn("Multi-tenant SES putTenantSuppressionAttributes not supported or failed: {}", ex.getMessage());
      }
      return getTenant(tenantName);
    } catch (SdkClientException | SesV2Exception exception) {
      throw providerUnavailable("Unable to provision Managed Email tenant", exception);
    }
  }

  @Override
  public TenantSnapshot getTenant(String tenantName) {
    try {
      Tenant tenant = null;
      try {
        tenant = ses.getTenant(GetTenantRequest.builder().tenantName(tenantName).build()).tenant();
      } catch (Exception ex) {
        log.debug("Multi-tenant SES getTenant not supported or failed: {}", ex.getMessage());
      }

      String reputation = null;
      if (tenant != null) {
        try {
          ReputationEntity entity =
              ses.getReputationEntity(
                      GetReputationEntityRequest.builder()
                          .reputationEntityType(ReputationEntityType.RESOURCE)
                          .reputationEntityReference(tenant.tenantArn())
                          .build())
                  .reputationEntity();
          if (entity != null) {
            reputation = entity.reputationImpactAsString();
          }
        } catch (Exception ignored) {
          // Reputation data may not be available immediately after tenant creation.
        }
      }

      Instant providerObservedAt = clock.instant();
      GetAccountResponse account = ses.getAccount(GetAccountRequest.builder().build());
      SendQuota quota = account == null ? null : account.sendQuota();
      Double maxSendRate = quota == null ? null : quota.maxSendRate();
      Double max24HourSend = quota == null ? null : quota.max24HourSend();
      Double sentLast24Hours = quota == null ? null : quota.sentLast24Hours();
      Boolean accountSendingEnabled = account == null ? null : account.sendingEnabled();
      Boolean productionAccessEnabled = account == null ? null : account.productionAccessEnabled();

      String sendingStatus = (tenant != null) ? tenant.sendingStatusAsString() : "ENABLED";

      return new TenantSnapshot(
          sendingStatus,
          reputation,
          maxSendRate,
          max24HourSend,
          sentLast24Hours,
          accountSendingEnabled,
          productionAccessEnabled,
          providerObservedAt);
    } catch (SdkClientException | SesV2Exception exception) {
      throw providerUnavailable("Unable to read Managed Email tenant status", exception);
    }
  }

  @Override
  public void ensureConfigurationSet(
      String tenantName, String configurationSetName, String snsTopicArn) {
    try {
      try {
        ses.createConfigurationSet(
            CreateConfigurationSetRequest.builder()
                .configurationSetName(configurationSetName)
                .reputationOptions(
                    ReputationOptions.builder().reputationMetricsEnabled(true).build())
                .sendingOptions(SendingOptions.builder().sendingEnabled(true).build())
                .build());
      } catch (AlreadyExistsException ignored) {
        ses.putConfigurationSetReputationOptions(
            PutConfigurationSetReputationOptionsRequest.builder()
                .configurationSetName(configurationSetName)
                .reputationMetricsEnabled(true)
                .build());
      }

      if (snsTopicArn != null && !snsTopicArn.isBlank() && snsTopicArn.startsWith("arn:aws:sns:")) {
        EventDestinationDefinition destination =
            EventDestinationDefinition.builder()
                .enabled(true)
                .matchingEventTypesWithStrings(EVENT_TYPES)
                .snsDestination(SnsDestination.builder().topicArn(snsTopicArn).build())
                .build();
        try {
          ses.createConfigurationSetEventDestination(
              CreateConfigurationSetEventDestinationRequest.builder()
                  .configurationSetName(configurationSetName)
                  .eventDestinationName(EVENT_DESTINATION_NAME)
                  .eventDestination(destination)
                  .build());
        } catch (AlreadyExistsException ignored) {
          ses.updateConfigurationSetEventDestination(
              UpdateConfigurationSetEventDestinationRequest.builder()
                  .configurationSetName(configurationSetName)
                  .eventDestinationName(EVENT_DESTINATION_NAME)
                  .eventDestination(destination)
                  .build());
        }
      }

      associate(tenantName, configurationSetArn(configurationSetName));
    } catch (SdkClientException | SesV2Exception exception) {
      throw providerUnavailable("Unable to configure Managed Email event resources", exception);
    }
  }

  @Override
  public DomainIdentitySnapshot ensureDomainIdentity(
      String tenantName, String configurationSetName, String domain) {
    try {
      try {
        ses.createEmailIdentity(
            CreateEmailIdentityRequest.builder()
                .emailIdentity(domain)
                .configurationSetName(configurationSetName)
                .dkimSigningAttributes(
                    DkimSigningAttributes.builder()
                        .nextSigningKeyLength(DkimSigningKeyLength.RSA_2048_BIT)
                        .build())
                .build());
      } catch (AlreadyExistsException ignored) {
        ses.putEmailIdentityConfigurationSetAttributes(
            PutEmailIdentityConfigurationSetAttributesRequest.builder()
                .emailIdentity(domain)
                .configurationSetName(configurationSetName)
                .build());
      }

      associate(tenantName, identityArn(domain));
      return identitySnapshot(domain);
    } catch (SdkClientException | SesV2Exception exception) {
      throw providerUnavailable("Unable to provision Managed Email domain", exception);
    }
  }

  @Override
  public DomainIdentitySnapshot getDomainIdentity(String domain) {
    try {
      return identitySnapshot(domain);
    } catch (SdkClientException | SesV2Exception exception) {
      throw providerUnavailable("Unable to refresh Managed Email domain", exception);
    }
  }

  @Override
  public void deleteDomainIdentity(String tenantName, String domain) {
    try {
      String arn = identityArn(domain);
      try {
        ses.deleteTenantResourceAssociation(
            DeleteTenantResourceAssociationRequest.builder()
                .tenantName(tenantName)
                .resourceArn(arn)
                .build());
      } catch (software.amazon.awssdk.services.sesv2.model.NotFoundException ignored) {
        // Already disassociated/deleted.
      } catch (Exception ex) {
        log.debug("Multi-tenant SES deleteTenantResourceAssociation ignored: {}", ex.getMessage());
      }
      try {
        ses.deleteEmailIdentity(DeleteEmailIdentityRequest.builder().emailIdentity(domain).build());
      } catch (software.amazon.awssdk.services.sesv2.model.NotFoundException ignored) {
        // Already deleted.
      }
    } catch (SdkClientException | SesV2Exception exception) {
      throw providerUnavailable("Unable to remove Managed Email domain", exception);
    }
  }

  private DomainIdentitySnapshot identitySnapshot(String domain) {
    GetEmailIdentityResponse response =
        ses.getEmailIdentity(GetEmailIdentityRequest.builder().emailIdentity(domain).build());
    DkimAttributes dkim = response.dkimAttributes();
    List<DkimRecord> records = new ArrayList<>();
    if (dkim != null) {
      for (String token : dkim.tokens()) {
        records.add(
            new DkimRecord(
                token + "._domainkey." + domain, token + ".dkim.amazonses.com"));
      }
    }
    return new DomainIdentitySnapshot(
        response.verificationStatusAsString(),
        dkim == null ? "UNKNOWN" : dkim.statusAsString(),
        List.copyOf(records));
  }

  private void associate(String tenantName, String resourceArn) {
    try {
      ses.createTenantResourceAssociation(
          CreateTenantResourceAssociationRequest.builder()
              .tenantName(tenantName)
              .resourceArn(resourceArn)
              .build());
    } catch (AlreadyExistsException ignored) {
      // Desired association already exists.
    } catch (Exception ex) {
      log.debug("Multi-tenant SES createTenantResourceAssociation ignored: {}", ex.getMessage());
    }
  }

  private String configurationSetArn(String configurationSetName) {
    return arnPrefix() + ":configuration-set/" + configurationSetName;
  }

  private String identityArn(String domain) {
    return arnPrefix() + ":identity/" + domain;
  }

  private String arnPrefix() {
    String region = properties.getAwsRegion().trim().toLowerCase(Locale.ROOT);
    String partition =
        region.startsWith("cn-")
            ? "aws-cn"
            : (region.startsWith("us-gov-") ? "aws-us-gov" : "aws");
    return "arn:" + partition + ":ses:" + region + ":" + accountId();
  }

  private String accountId() {
    String current = accountId.get();
    if (current != null) return current;
    String resolved = sts.getCallerIdentity().account();
    accountId.compareAndSet(null, resolved);
    return accountId.get();
  }

  private ServiceUnavailableException providerUnavailable(String message, RuntimeException cause) {
    log.error("AWS SES Managed Email failure [{}]: {}", message, cause.getMessage(), cause);
    return new ServiceUnavailableException(message + ": " + cause.getMessage(), cause);
  }
}
