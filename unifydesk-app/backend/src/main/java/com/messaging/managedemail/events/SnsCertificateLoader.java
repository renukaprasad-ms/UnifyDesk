package com.messaging.managedemail.events;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SnsCertificateLoader {

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public X509Certificate load(URI uri, int maxBytes) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200
          || response.body() == null
          || response.body().length == 0
          || response.body().length > maxBytes) {
        throw new IllegalArgumentException("Invalid SNS signing certificate response");
      }
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      return (X509Certificate)
          factory.generateCertificate(new ByteArrayInputStream(response.body()));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("Unable to retrieve SNS signing certificate", exception);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Unable to retrieve SNS signing certificate", exception);
    }
  }
}
