package com.messaging.managedemail.delivery;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Component
public class ManagedSesClientProvider {

  private final ConcurrentHashMap<String, SesV2Client> clients = new ConcurrentHashMap<>();

  public SesV2Client client(String region) {
    if (region == null || region.isBlank()) {
      throw new IllegalArgumentException("Managed Email region is required");
    }
    return clients.computeIfAbsent(region, this::build);
  }

  private SesV2Client build(String region) {
    return SesV2Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @PreDestroy
  void close() {
    clients.values().forEach(
        client -> {
          try {
            client.close();
          } catch (RuntimeException ignored) {
          }
        });
    clients.clear();
  }
}
