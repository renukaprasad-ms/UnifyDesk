package com.messaging.managedemail.events;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SnsSubscriptionConfirmer {

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public void confirm(URI subscribeUri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(subscribeUri).timeout(Duration.ofSeconds(5)).GET().build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("SNS subscription confirmation failed");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("SNS subscription confirmation interrupted", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("SNS subscription confirmation failed", exception);
    }
  }
}
