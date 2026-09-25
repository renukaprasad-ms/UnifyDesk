package com.messaging.emailtemplate.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class EmailVariableService {
  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

  public List<String> extract(String... values) {
    LinkedHashSet<String> variables = new LinkedHashSet<>();
    if (values == null) return List.of();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      Matcher matcher = VARIABLE_PATTERN.matcher(value);
      while (matcher.find()) variables.add(matcher.group(1));
    }
    return List.copyOf(variables);
  }

  public Rendered render(String value, Map<String, String> values) {
    if (value == null) return new Rendered(null, List.of());
    Map<String, String> safeValues = values == null ? Map.of() : values;
    Matcher matcher = VARIABLE_PATTERN.matcher(value);
    StringBuffer output = new StringBuffer();
    LinkedHashSet<String> unresolved = new LinkedHashSet<>();
    while (matcher.find()) {
      String variable = matcher.group(1);
      String replacement = safeValues.get(variable);
      if (replacement == null || replacement.isBlank()) {
        unresolved.add(variable);
        matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(0)));
      } else {
        matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
      }
    }
    matcher.appendTail(output);
    return new Rendered(output.toString(), List.copyOf(unresolved));
  }

  public record Rendered(String value, List<String> unresolvedVariables) {}
}
