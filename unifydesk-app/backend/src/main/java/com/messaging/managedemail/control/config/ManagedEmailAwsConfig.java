package com.messaging.managedemail.control.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
public class ManagedEmailAwsConfig {

  @Bean
  SesV2Client managedEmailSesV2Client(ManagedEmailProperties properties) {
    return SesV2Client.builder().region(Region.of(properties.getAwsRegion())).build();
  }

  @Bean
  StsClient managedEmailStsClient(ManagedEmailProperties properties) {
    return StsClient.builder().region(Region.of(properties.getAwsRegion())).build();
  }
}
