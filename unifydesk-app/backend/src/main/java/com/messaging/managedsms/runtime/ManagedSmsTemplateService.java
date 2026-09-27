package com.messaging.managedsms.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.net.IDN;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("managedSmsRuntimeTemplateService")
@RequiredArgsConstructor
public class ManagedSmsTemplateService {

  private static final Pattern NUMERIC = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
  private static final Pattern CALLBACK = Pattern.compile("\\+?[0-9]{8,15}");
  private static final Pattern EMAIL =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern ALPHANUMERIC =
      Pattern.compile("[\\p{L}\\p{N} .,_\\-/:@()+]+");
  private static final Pattern IPV4 =
      Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");

  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ObjectMapper objectMapper;

  public DraftTemplate renderForDraft(
      Long companyId, Long senderId, Long templateId, Map<String, String> variables) {
    ManagedSmsRuntimeRepository.ManagedTemplateRuntime runtime =
        runtimeRepository
            .findTemplateForCompose(companyId, senderId, templateId)
            .filter(ManagedSmsRuntimeRepository.ManagedTemplateRuntime::activeForCompose)
            .orElseThrow(() -> new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY"));
    RenderedTemplate rendered =
        render(
            runtime.approvedBody(),
            runtime.bodyFingerprint(),
            runtime.variablesSchemaJson(),
            runtime.ctaPolicyJson(),
            variables);
    return new DraftTemplate(
        runtime.templateId(), runtime.versionNo(), rendered.body(), rendered.variables());
  }

  public RenderedTemplate render(
      String approvedBody,
      String expectedFingerprint,
      String variablesSchemaJson,
      String ctaPolicyJson,
      Map<String, String> variables) {
    if (approvedBody == null || approvedBody.isBlank()) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }
    verifyFingerprint(approvedBody, expectedFingerprint);

    List<VariableSpec> specs = parseVariableSpecs(variablesSchemaJson);
    Map<String, String> input = variables == null ? Map.of() : variables;

    LinkedHashSet<String> allowed = new LinkedHashSet<>();
    for (VariableSpec spec : specs) allowed.add(spec.key());
    if (!allowed.equals(new LinkedHashSet<>(input.keySet()))) {
      throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    }

    LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
    String rendered = approvedBody;
    for (VariableSpec spec : specs) {
      String value = normalizeAndValidate(spec, input.get(spec.key()));
      normalized.put(spec.key(), value);
      rendered = replacePlaceholder(rendered, spec.key(), value);
    }
    if (rendered.contains("{#var#}")) {
      throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    }
    validateCtaPolicy(ctaPolicyJson, specs, normalized);
    return new RenderedTemplate(rendered, Map.copyOf(normalized), List.copyOf(specs));
  }

  public List<VariableSpec> parseVariableSpecs(String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank()) return List.of();
    try {
      JsonNode root = objectMapper.readTree(schemaJson);
      JsonNode array = root.isArray() ? root : root.path("variables");
      if (!array.isArray()) {
        throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
      }
      List<VariableSpec> specs = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      int index = 0;
      for (JsonNode node : array) {
        String key = node.path("key").asText("").trim();
        if (key.isBlank() || !seen.add(key)) {
          throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
        }
        String type = node.path("type").asText("ALPHANUMERIC").trim().toUpperCase(Locale.ROOT);
        int maxLength = node.path("maxLength").asInt(100);
        if (maxLength <= 0 || maxLength > 1000) {
          throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
        }
        String providerKey = node.path("providerKey").asText("").trim();
        if (providerKey.isBlank()) providerKey = "VAR" + (++index);
        specs.add(new VariableSpec(key, type, maxLength, providerKey));
      }
      return List.copyOf(specs);
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    }
  }

  private String normalizeAndValidate(VariableSpec spec, String raw) {
    if (raw == null) throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    String value = raw.trim();
    if (value.isEmpty()
        || value.length() > spec.maxLength()
        || value.contains("{#var#}")
        || value.contains("{{")
        || value.contains("}}")) {
      throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    }
    boolean valid =
        switch (spec.type()) {
          case "NUMERIC", "NUMBER" -> NUMERIC.matcher(value).matches();
          case "URL" -> validUrl(value);
          case "CALLBACK", "PHONE" -> CALLBACK.matcher(value).matches();
          case "EMAIL" -> EMAIL.matcher(value).matches();
          case "ALPHANUMERIC" -> ALPHANUMERIC.matcher(value).matches();
          case "TEXT" -> value.chars().noneMatch(Character::isISOControl);
          default -> false;
        };
    if (!valid) throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    return value;
  }

  private boolean validUrl(String value) {
    try {
      parseUrl(value);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private void validateCtaPolicy(
      String ctaPolicyJson, List<VariableSpec> specs, Map<String, String> values) {
    Map<String, VariableSpec> byKey = new LinkedHashMap<>();
    Set<String> requiredUrl = new LinkedHashSet<>();
    Set<String> requiredCallback = new LinkedHashSet<>();
    for (VariableSpec spec : specs) {
      byKey.put(spec.key(), spec);
      if ("URL".equals(spec.type())) requiredUrl.add(spec.key());
      if ("CALLBACK".equals(spec.type()) || "PHONE".equals(spec.type())) {
        requiredCallback.add(spec.key());
      }
    }
    if (requiredUrl.isEmpty() && requiredCallback.isEmpty()) return;

    try {
      if (ctaPolicyJson == null || ctaPolicyJson.isBlank()) {
        throw ctaRejected();
      }
      JsonNode policy = objectMapper.readTree(ctaPolicyJson);
      if (!policy.isObject() || policy.path("policyVersion").asInt(-1) != 2) {
        throw ctaRejected();
      }

      Set<String> coveredUrl = new HashSet<>();
      JsonNode urlRules = policy.path("urlRules");
      if (!urlRules.isArray()) throw ctaRejected();
      for (JsonNode rule : urlRules) {
        String key = rule.path("key").asText("").trim();
        if (!requiredUrl.contains(key) || !coveredUrl.add(key)) throw ctaRejected();
        validateUrlRule(rule, values.get(key));
      }

      Set<String> coveredCallback = new HashSet<>();
      JsonNode callbackRules = policy.path("callbackRules");
      if (!callbackRules.isArray()) throw ctaRejected();
      for (JsonNode rule : callbackRules) {
        String key = rule.path("key").asText("").trim();
        if (!requiredCallback.contains(key) || !coveredCallback.add(key)) throw ctaRejected();
        validateCallbackRule(rule, values.get(key));
      }

      if (!coveredUrl.equals(requiredUrl) || !coveredCallback.equals(requiredCallback)) {
        throw ctaRejected();
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ctaRejected();
    }
  }

  private void validateUrlRule(JsonNode rule, String rawValue) {
    ParsedUrl actual = parseUrl(rawValue);
    String mode = rule.path("mode").asText("").trim().toUpperCase(Locale.ROOT);
    switch (mode) {
      case "EXACT_VALUES" -> {
        JsonNode allowed = rule.path("allowedValues");
        if (!allowed.isArray() || allowed.isEmpty()) throw ctaRejected();
        boolean matched = false;
        for (JsonNode item : allowed) {
          if (item.isTextual() && actual.canonical().equals(parseUrl(item.asText()).canonical())) {
            matched = true;
            break;
          }
        }
        if (!matched) throw ctaRejected();
      }
      case "HOST_ALLOWLIST" -> {
        if (rule.path("requireHttps").asBoolean(false)
            && !"https".equals(actual.scheme())) {
          throw ctaRejected();
        }
        if (!safeHostPort(actual)) throw ctaRejected();
        JsonNode allowedHosts = rule.path("allowedHosts");
        if (!allowedHosts.isArray() || allowedHosts.isEmpty()) throw ctaRejected();
        boolean allowSubdomains = rule.path("allowSubdomains").asBoolean(false);
        boolean hostMatched = false;
        for (JsonNode item : allowedHosts) {
          if (!item.isTextual()) continue;
          String allowed = normalizeHost(item.asText());
          if (actual.host().equals(allowed)
              || (allowSubdomains && actual.host().endsWith("." + allowed))) {
            hostMatched = true;
            break;
          }
        }
        if (!hostMatched) throw ctaRejected();

        JsonNode prefixes = rule.path("allowedPathPrefixes");
        if (prefixes.isArray() && !prefixes.isEmpty()) {
          boolean pathMatched = false;
          for (JsonNode item : prefixes) {
            if (!item.isTextual()) continue;
            String prefix = normalizePathPrefix(item.asText());
            if (pathMatchesPrefix(actual.path(), prefix)) {
              pathMatched = true;
              break;
            }
          }
          if (!pathMatched) throw ctaRejected();
        }
      }
      default -> throw ctaRejected();
    }
  }

  private void validateCallbackRule(JsonNode rule, String rawValue) {
    String actual = normalizeCallback(rawValue);
    JsonNode allowed = rule.path("allowedValues");
    if (!allowed.isArray() || allowed.isEmpty()) throw ctaRejected();
    for (JsonNode item : allowed) {
      if (item.isTextual() && actual.equals(normalizeCallback(item.asText()))) return;
    }
    throw ctaRejected();
  }

  private ParsedUrl parseUrl(String value) {
    try {
      URI uri = URI.create(value).normalize();
      String scheme = lower(uri.getScheme());
      if (!"http".equals(scheme) && !"https".equals(scheme)) throw ctaRejected();
      if (uri.getUserInfo() != null) throw ctaRejected();

      String host = uri.getHost();
      int port = uri.getPort();
      if (host == null) {
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank() || authority.contains("@")) {
          throw ctaRejected();
        }
        String candidate = authority;
        int colon = candidate.lastIndexOf(':');
        if (colon > 0
            && candidate.indexOf(':') == colon
            && candidate.substring(colon + 1).matches("[0-9]+")) {
          port = Integer.parseInt(candidate.substring(colon + 1));
          candidate = candidate.substring(0, colon);
        }
        host = candidate;
      }
      host = normalizeHost(host);
      if (looksLikeIpLiteral(host)) throw ctaRejected();

      if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
        port = -1;
      }
      String path = uri.getRawPath();
      if (path == null || path.isEmpty()) path = "/";
      String lowerPath = path.toLowerCase(Locale.ROOT);
      if (lowerPath.contains("%2e")
          || lowerPath.contains("%2f")
          || lowerPath.contains("%5c")
          || path.contains("\\")) {
        throw ctaRejected();
      }
      StringBuilder canonical = new StringBuilder(scheme).append("://").append(host);
      if (port >= 0) canonical.append(':').append(port);
      canonical.append(path);
      if (uri.getRawQuery() != null) canonical.append('?').append(uri.getRawQuery());
      if (uri.getRawFragment() != null) canonical.append('#').append(uri.getRawFragment());
      return new ParsedUrl(scheme, host, path, port, canonical.toString());
    } catch (BadRequestException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw ctaRejected();
    }
  }

  private boolean safeHostPort(ParsedUrl url) {
    return url.port() < 0
        || ("https".equals(url.scheme()) && url.port() == 443)
        || ("http".equals(url.scheme()) && url.port() == 80);
  }

  private String normalizeHost(String raw) {
    try {
      String host = IDN.toASCII(raw.trim(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
      if (host.isBlank() || host.startsWith(".") || host.endsWith(".")) throw ctaRejected();
      if (looksLikeIpLiteral(host)) throw ctaRejected();
      return host;
    } catch (BadRequestException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw ctaRejected();
    }
  }

  private boolean looksLikeIpLiteral(String host) {
    if (host == null) return true;
    String value = host.toLowerCase(Locale.ROOT);
    return IPV4.matcher(value).matches()
        || value.contains(":")
        || value.matches("[0-9]+")
        || value.matches("0x[0-9a-f]+");
  }

  private boolean pathMatchesPrefix(String path, String prefix) {
    if ("/".equals(prefix)) return path.startsWith("/");
    if (path.equals(prefix)) return true;
    String segmentPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
    return path.startsWith(segmentPrefix);
  }

  private String normalizePathPrefix(String raw) {
    if (raw == null || raw.isBlank() || !raw.startsWith("/")) throw ctaRejected();
    try {
      String path = URI.create("https://policy.invalid" + raw).normalize().getRawPath();
      return path == null || path.isBlank() ? "/" : path;
    } catch (RuntimeException exception) {
      throw ctaRejected();
    }
  }

  private String normalizeCallback(String raw) {
    if (raw == null) throw ctaRejected();
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.length() < 8 || digits.length() > 15) throw ctaRejected();
    return digits;
  }

  private String replacePlaceholder(String body, String key, String value) {
    int generic = body.indexOf("{#var#}");
    if (generic >= 0) {
      return body.substring(0, generic) + value + body.substring(generic + 7);
    }
    String named = "{{" + key + "}}";
    if (body.contains(named)) return body.replace(named, value);
    named = "{" + key + "}";
    if (body.contains(named)) return body.replace(named, value);
    throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
  }

  private void verifyFingerprint(String body, String expectedFingerprint) {
    if (expectedFingerprint == null || expectedFingerprint.isBlank()) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }
    try {
      byte[] expected = HexFormat.of().parseHex(expectedFingerprint.trim().toLowerCase(Locale.ROOT));
      byte[] actual =
          MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
      if (expected.length != actual.length || !MessageDigest.isEqual(expected, actual)) {
        throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }
  }

  private BadRequestException ctaRejected() {
    return new BadRequestException("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  private String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private record ParsedUrl(
      String scheme, String host, String path, int port, String canonical) {}

  public record VariableSpec(String key, String type, int maxLength, String providerKey) {}

  public record RenderedTemplate(
      String body, Map<String, String> variables, List<VariableSpec> variableSpecs) {}

  public record DraftTemplate(
      Long templateId, Long versionNo, String body, Map<String, String> variables) {}
}
