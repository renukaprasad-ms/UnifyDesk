package com.messaging.managedemail.control.aws;

import java.util.List;

public interface ManagedEmailSesGateway {

  TenantSnapshot ensureTenant(String tenantName);

  TenantSnapshot getTenant(String tenantName);

  void ensureConfigurationSet(String tenantName, String configurationSetName, String snsTopicArn);

  DomainIdentitySnapshot ensureDomainIdentity(
      String tenantName, String configurationSetName, String domain);

  DomainIdentitySnapshot getDomainIdentity(String domain);

  void deleteDomainIdentity(String tenantName, String domain);

  record TenantSnapshot(
      String sendingStatus,
      String reputationStatus,
      Double maxSendRate,
      Double max24HourSend,
      boolean productionAccessEnabled) {
    public TenantSnapshot(String sendingStatus, String reputationStatus) {
      this(sendingStatus, reputationStatus, null, null, false);
    }
  }

  record DkimRecord(String name, String value) {}

  record DomainIdentitySnapshot(
      String verificationStatus, String dkimStatus, List<DkimRecord> dkimRecords) {}
}
