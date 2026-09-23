package com.messaging.broadcast.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.channel.enums.ChannelType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class BroadcastTemplateRenderer {

  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*\\}\\}");
  private static final Pattern UNSAFE_URI_ATTRIBUTE =
      Pattern.compile(
          "(?i)(?:href|src)\\s*=\\s*[\"']\\s*(?:javascript|data|vbscript)\\s*:");

  private final ObjectMapper objectMapper;

  public RenderedContent render(
      String subject,
      String bodyText,
      String bodyHtml,
      String globalVariablesJson,
      BroadcastRecipient recipient,
      ChannelType channelType) {
    Map<String, String> values = values(globalVariablesJson, recipient, channelType);
    RenderedValue renderedSubject = renderValue(subject, values, false);
    RenderedValue renderedBody = renderValue(bodyText, values, false);
    RenderedValue renderedHtml = renderValue(bodyHtml, values, true);

    LinkedHashSet<String> unresolved = new LinkedHashSet<>();
    unresolved.addAll(renderedSubject.unresolved());
    unresolved.addAll(renderedBody.unresolved());
    unresolved.addAll(renderedHtml.unresolved());

    boolean unsafeHtml =
        renderedHtml.value() != null && UNSAFE_URI_ATTRIBUTE.matcher(renderedHtml.value()).find();
    Map<String, String> templateVariables =
        templateVariables(subject, bodyText, bodyHtml, values);

    return new RenderedContent(
        renderedSubject.value(),
        renderedBody.value(),
        renderedHtml.value(),
        List.copyOf(unresolved),
        unsafeHtml,
        templateVariables);
  }

  private Map<String, String> values(
      String globalVariablesJson, BroadcastRecipient recipient, ChannelType channelType) {
    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    readMetadata(globalVariablesJson).forEach(
        (key, value) -> {
          String clean = trimToNull(value);
          if (clean == null) return;
          values.put(key, clean);
          values.put(normalizeKey(key), clean);
        });

    values.put("destination", recipient.getDestination());
    if (channelType == ChannelType.EMAIL) {
      values.put("email", recipient.getDestination());
    }
    if (channelType == ChannelType.SMS || channelType == ChannelType.WHATSAPP) {
      values.put("phone", recipient.getDestination());
      values.put("mobile", recipient.getDestination());
    }

    String displayName = trimToNull(recipient.getDisplayName());
    if (displayName != null) {
      values.put("name", displayName);
      values.put("display_name", displayName);
      values.put("full_name", displayName);

      String[] parts = displayName.trim().split("\\s+", 2);
      if (parts.length > 0 && !parts[0].isBlank()) values.put("first_name", parts[0]);
      if (parts.length > 1 && !parts[1].isBlank()) values.put("last_name", parts[1]);
    }

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    values.put("current_date", today.toString());
    values.put("current_year", Integer.toString(today.getYear()));

    Map<String, String> metadata = readMetadata(recipient.getPersonalizationJson());
    metadata.forEach(
        (key, value) -> {
          String clean = trimToNull(value);
          if (clean == null) return;
          values.put(key, clean);
          values.put(normalizeKey(key), clean);
        });

    return values;
  }

  private Map<String, String> readMetadata(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      Map<String, String> value =
          objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      return value == null ? Map.of() : value;
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private RenderedValue renderValue(String value, Map<String, String> values, boolean escapeHtml) {
    if (value == null) return new RenderedValue(null, List.of());
    Matcher matcher = VARIABLE_PATTERN.matcher(value);
    StringBuffer output = new StringBuffer();
    LinkedHashSet<String> unresolved = new LinkedHashSet<>();

    while (matcher.find()) {
      String variable = matcher.group(1);
      String replacement = resolve(variable, values);
      if (replacement == null) {
        unresolved.add(variable);
        matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(0)));
      } else {
        String safeReplacement = escapeHtml ? HtmlUtils.htmlEscape(replacement) : replacement;
        matcher.appendReplacement(output, Matcher.quoteReplacement(safeReplacement));
      }
    }
    matcher.appendTail(output);
    return new RenderedValue(output.toString(), List.copyOf(unresolved));
  }

  private Map<String, String> templateVariables(
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> values) {
    LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
    for (String template : List.of(
        subject == null ? "" : subject,
        bodyText == null ? "" : bodyText,
        bodyHtml == null ? "" : bodyHtml)) {
      Matcher matcher = VARIABLE_PATTERN.matcher(template);
      while (matcher.find()) {
        String variable = matcher.group(1);
        String value = resolve(variable, values);
        if (value != null) resolved.putIfAbsent(variable, value);
      }
    }
    return Map.copyOf(resolved);
  }

  private String resolve(String variable, Map<String, String> values) {
    String exact = trimToNull(values.get(variable));
    if (exact != null) return exact;
    return trimToNull(values.get(normalizeKey(variable)));
  }

  private String normalizeKey(String key) {
    return key == null
        ? ""
        : key.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record RenderedContent(
      String subject,
      String bodyText,
      String bodyHtml,
      List<String> unresolvedVariables,
      boolean unsafeHtml,
      Map<String, String> templateVariables) {}

  private record RenderedValue(String value, List<String> unresolved) {}
}
