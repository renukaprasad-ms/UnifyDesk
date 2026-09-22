package com.messaging.broadcast.ses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderAuthMode;
import com.messaging.channel.credential.CredentialCipher;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@Component
@RequiredArgsConstructor
public class AmazonSesClientManager {

  private final CredentialCipher credentialCipher;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<Long, ClientHolder> clients = new ConcurrentHashMap<>();

  public SesV2Client client(BroadcastSenderConnection connection) {
    if (connection.getId() == null) {
      throw new IllegalArgumentException("SES connection id is required");
    }
    String fingerprint =
        connection.getRegion()
            + "|"
            + connection.getAuthMode()
            + "|"
            + Objects.toString(connection.getRoleArn(), "")
            + "|"
            + Objects.toString(connection.getUpdatedAt(), "");
    ClientHolder holder =
        clients.compute(
            connection.getId(),
            (id, existing) -> {
              if (existing != null && existing.fingerprint().equals(fingerprint)) return existing;
              if (existing != null) existing.close();
              return build(connection, fingerprint);
            });
    return holder.ses();
  }

  public void invalidate(Long connectionId) {
    if (connectionId == null) return;
    ClientHolder holder = clients.remove(connectionId);
    if (holder != null) holder.close();
  }

  private ClientHolder build(BroadcastSenderConnection connection, String fingerprint) {
    Region region = Region.of(connection.getRegion());
    if (connection.getAuthMode() == BroadcastSenderAuthMode.ACCESS_KEYS) {
      SesStoredCredentials stored = storedCredentials(connection);
      AwsCredentialsProvider provider =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(stored.accessKeyId(), stored.secretAccessKey()));
      SesV2Client ses =
          SesV2Client.builder().region(region).credentialsProvider(provider).build();
      return new ClientHolder(fingerprint, ses, null, null);
    }

    StsClient sts =
        StsClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    AssumeRoleRequest.Builder assumeRole =
        AssumeRoleRequest.builder()
            .roleArn(connection.getRoleArn())
            .roleSessionName("messaging-broadcast-" + connection.getId());
    if (connection.getExternalId() != null && !connection.getExternalId().isBlank()) {
      assumeRole.externalId(connection.getExternalId());
    }
    StsAssumeRoleCredentialsProvider provider =
        StsAssumeRoleCredentialsProvider.builder()
            .stsClient(sts)
            .refreshRequest(assumeRole.build())
            .build();
    SesV2Client ses =
        SesV2Client.builder().region(region).credentialsProvider(provider).build();
    return new ClientHolder(fingerprint, ses, provider, sts);
  }

  private SesStoredCredentials storedCredentials(BroadcastSenderConnection connection) {
    if (connection.getEncryptedCredentials() == null
        || connection.getEncryptedCredentials().isBlank()) {
      throw new IllegalStateException("Amazon SES access-key credentials are missing");
    }
    try {
      String json = credentialCipher.decrypt(connection.getEncryptedCredentials());
      return objectMapper.readValue(json, SesStoredCredentials.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read Amazon SES credentials", exception);
    }
  }

  @PreDestroy
  void close() {
    clients.values().forEach(ClientHolder::close);
    clients.clear();
  }

  private record ClientHolder(
      String fingerprint,
      SesV2Client ses,
      StsAssumeRoleCredentialsProvider assumeRoleProvider,
      StsClient sts) {

    void close() {
      try {
        ses.close();
      } catch (RuntimeException ignored) {
      }
      if (assumeRoleProvider != null) {
        try {
          assumeRoleProvider.close();
        } catch (RuntimeException ignored) {
        }
      }
      if (sts != null) {
        try {
          sts.close();
        } catch (RuntimeException ignored) {
        }
      }
    }
  }
}
