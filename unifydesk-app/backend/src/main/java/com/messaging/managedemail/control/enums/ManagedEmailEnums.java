package com.messaging.managedemail.control.enums;

public final class ManagedEmailEnums {

  private ManagedEmailEnums() {}

  public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    ERROR,
    DELETING,
    DELETED
  }

  public enum SendingStatus {
    ENABLED,
    DISABLED,
    PROVIDER_DISABLED
  }

  public enum RampStage {
    NEW,
    RAMPING,
    ESTABLISHED,
    RESTRICTED
  }

  public enum DomainType {
    CUSTOMER_DOMAIN,
    UNIFYDESK_HOSTED
  }

  public enum DomainStatus {
    PENDING_APPROVAL,
    PROVISIONING,
    PENDING_DNS,
    VERIFYING,
    ACTIVE,
    SUSPENDED_VERIFICATION,
    SUSPENDED_POLICY,
    ERROR,
    DELETING,
    DELETED,
    RETIRED,
    REJECTED
  }

  public enum VerificationStatus {
    PENDING,
    SUCCESS,
    FAILED,
    UNKNOWN
  }

  public enum DkimStatus {
    PENDING,
    SUCCESS,
    FAILED,
    UNKNOWN
  }

  public enum DmarcStatus {
    UNKNOWN,
    ABSENT,
    PRESENT_MONITOR,
    PRESENT_ENFORCING,
    INVALID
  }

  public enum DnsRecordPurpose {
    DKIM,
    DMARC,
    MAIL_FROM_MX,
    MAIL_FROM_SPF,
    OTHER_PROVIDER_REQUIRED
  }

  public enum DnsRecordType {
    CNAME,
    TXT,
    MX
  }

  public enum DnsRecordStatus {
    PENDING,
    PRESENT,
    MISSING,
    MISMATCH,
    NOT_APPLICABLE
  }

  public enum DnsRecordSource {
    SES,
    UNIFYDESK
  }

  public enum ProvisioningResourceType {
    TENANT,
    DOMAIN,
    CONNECTION
  }

  public enum ProvisioningOperationType {
    ENSURE_TENANT,
    ENSURE_CONFIGURATION_SET,
    ENSURE_DOMAIN_IDENTITY,
    ENSURE_HOSTED_DNS,
    REFRESH_DOMAIN_VERIFICATION,
    DELETE_DOMAIN_IDENTITY,
    DELETE_TENANT_RESOURCES
  }

  public enum AdminActionType {
    SUSPEND,
    RESUME
  }

  public enum ProvisioningOperationStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SUCCEEDED,
    FAILED,
    CANCELLED
  }
}
