package com.messaging.emailprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EmailProviderApplication {

  public static void main(String[] args) {
    SpringApplication.run(EmailProviderApplication.class, args);
  }
}
