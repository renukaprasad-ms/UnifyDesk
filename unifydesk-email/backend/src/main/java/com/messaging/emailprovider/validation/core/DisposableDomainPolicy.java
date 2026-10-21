package com.messaging.emailprovider.validation.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DisposableDomainPolicy {

  private final Set<String> domains;

  public DisposableDomainPolicy() {
    this.domains = load();
  }

  public boolean isDisposable(String domain) {
    return domains.contains(domain.toLowerCase(Locale.ROOT));
  }

  private Set<String> load() {
    ClassPathResource resource = new ClassPathResource("email-validation/disposable-domains.txt");
    if (!resource.exists()) {
      return Set.of();
    }

    Set<String> values = new HashSet<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .filter(line -> !line.startsWith("#"))
          .map(line -> line.toLowerCase(Locale.ROOT))
          .forEach(values::add);
      return Set.copyOf(values);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to load disposable domain data", exception);
    }
  }
}
