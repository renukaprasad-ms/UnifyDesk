package com.messaging.managedsms.control.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.VariableDefinition;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.VariableType;
import java.net.IDN;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ManagedSmsCtaPolicyService {

  private static final int POLICY_VERSION = 2;
  private static final String EXACT_VALUES = "EXACT_VALUES";
  private static final String HOST_ALLOWLIST = "HOST_ALLOWLIST";

  private final ObjectMapper objectMapper;
  private final ManagedSmsJsonSupport jsonSupport;

  public ManagedSmsCtaPolicyService(ObjectMapper objectMapper, ManagedSmsJsonSupport jsonSupport) {
    this.objectMapper = objectMapper;
    this.jsonSupport = jsonSupport;
  }

  public String finalizeForActivation(String variablesJson, JsonNode requestedPolicy) {
    List<VariableDefinition> variables = jsonSupport.variables(variablesJson);
    JsonNode source =
        requestedPolicy == null || requestedPolicy.isNull()
            ? objectMapper.createObjectNode()
            : requestedPolicy;
    return jsonSupport.value(validateAndNormalize(variables, source, true).normalized());
  }

  public void requireReadyForActivation(String variablesJson, String policyJson) {
    try {
      JsonNode stored = objectMapper.readTree(policyJson);
      validateAndNormalize(jsonSupport.variables(variablesJson), stored, true);
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ctaError("CTA approval policy is invalid");
    }
  }

  public Map<String, String> validateRuntimeValues(
      String policyJson, List<VariableDefinition> variables, Map<String, String> values) {
    ValidatedPolicy policy;
    try {
      JsonNode stored = objectMapper.readTree(policyJson);
      policy = validateAndNormalize(variables, stored, true);
    } catch (BadRequestException exception) {
      return failAllCtaVariables(variables, "CTA approval policy is invalid");
    } catch (Exception exception) {
      return failAllCtaVariables(variables, "CTA approval policy is invalid");
    }

    Map<String, String> errors = new LinkedHashMap<>();
    for (VariableDefinition variable : variables) {
      if (variable.type() == VariableType.URL) {
        UrlRule rule = policy.urlRules().get(variable.key());
        String value = values.get(variable.key());
        if (value == null || value.isBlank() || rule == null || !urlAllowed(rule, value)) {
          errors.put(variable.key(), "CTA URL is not approved");
        }
      } else if (variable.type() == VariableType.CALLBACK) {
        CallbackRule rule = policy.callbackRules().get(variable.key());
        String value = values.get(variable.key());
        if (value == null || value.isBlank() || rule == null || !callbackAllowed(rule, value)) {
          errors.put(variable.key(), "CTA callback is not approved");
        }
      }
    }
    return Map.copyOf(errors);
  }

  private ValidatedPolicy validateAndNormalize(
      List<VariableDefinition> variables, JsonNode source, boolean requireComplete) {
    Set<String> urlKeys = keysOfType(variables, VariableType.URL);
    Set<String> callbackKeys = keysOfType(variables, VariableType.CALLBACK);
    boolean hasCtaVariables = !urlKeys.isEmpty() || !callbackKeys.isEmpty();

    if (!source.isObject()) {
      throw ctaError("CTA policy must be an object");
    }
    JsonNode versionNode = source.get("policyVersion");
    if (versionNode == null || !versionNode.isInt() || versionNode.intValue() != POLICY_VERSION) {
      if (!hasCtaVariables && source.size() == 0) {
        return emptyPolicy();
      }
      throw ctaError("CTA policyVersion=2 is required");
    }

    Map<String, UrlRule> urlRules = parseUrlRules(source.get("urlRules"), urlKeys);
    Map<String, CallbackRule> callbackRules =
        parseCallbackRules(source.get("callbackRules"), callbackKeys);

    if (requireComplete) {
      if (!urlRules.keySet().equals(urlKeys)) {
        throw ctaError("Every URL variable requires exactly one approved rule");
      }
      if (!callbackRules.keySet().equals(callbackKeys)) {
        throw ctaError("Every callback variable requires exactly one approved rule");
      }
    }

    ObjectNode normalized = objectMapper.createObjectNode();
    normalized.put("policyVersion", POLICY_VERSION);
    ArrayNode normalizedUrls = normalized.putArray("urlRules");
    for (UrlRule rule : urlRules.values()) {
      ObjectNode node = normalizedUrls.addObject();
      node.put("key", rule.key());
      node.put("mode", rule.mode());
      if (EXACT_VALUES.equals(rule.mode())) {
        ArrayNode allowed = node.putArray("allowedValues");
        rule.allowedValues().forEach(allowed::add);
      } else {
        ArrayNode hosts = node.putArray("allowedHosts");
        rule.allowedHosts().forEach(hosts::add);
        node.put("allowSubdomains", rule.allowSubdomains());
        node.put("requireHttps", rule.requireHttps());
        ArrayNode paths = node.putArray("allowedPathPrefixes");
        rule.allowedPathPrefixes().forEach(paths::add);
      }
    }
    ArrayNode normalizedCallbacks = normalized.putArray("callbackRules");
    for (CallbackRule rule : callbackRules.values()) {
      ObjectNode node = normalizedCallbacks.addObject();
      node.put("key", rule.key());
      ArrayNode allowed = node.putArray("allowedValues");
      rule.allowedValues().forEach(allowed::add);
    }
    return new ValidatedPolicy(
        Map.copyOf(urlRules), Map.copyOf(callbackRules), normalized);
  }

  private ValidatedPolicy emptyPolicy() {
    ObjectNode normalized = objectMapper.createObjectNode();
    normalized.put("policyVersion", POLICY_VERSION);
    normalized.putArray("urlRules");
    normalized.putArray("callbackRules");
    return new ValidatedPolicy(Map.of(), Map.of(), normalized);
  }

  private Map<String, UrlRule> parseUrlRules(JsonNode rulesNode, Set<String> allowedKeys) {
    if (rulesNode == null || rulesNode.isNull()) {
      return Map.of();
    }
    if (!rulesNode.isArray()) {
      throw ctaError("urlRules must be an array");
    }
    Map<String, UrlRule> result = new LinkedHashMap<>();
    for (JsonNode ruleNode : rulesNode) {
      if (!ruleNode.isObject()) throw ctaError("URL rule must be an object");
      String key = requiredText(ruleNode, "key");
      if (!allowedKeys.contains(key)) {
        throw ctaError("URL rule references a non-URL variable");
      }
      if (result.containsKey(key)) {
        throw ctaError("Only one URL rule is allowed per variable");
      }
      String mode = requiredText(ruleNode, "mode");
      if (EXACT_VALUES.equals(mode)) {
        List<String> allowedValues =
            requiredTextArray(ruleNode.get("allowedValues"), "allowedValues").stream()
                .map(value -> parseUrl(value).canonical())
                .distinct()
                .toList();
        if (allowedValues.isEmpty()) throw ctaError("EXACT_VALUES requires allowedValues");
        result.put(
            key,
            new UrlRule(key, mode, allowedValues, List.of(), false, true, List.of()));
      } else if (HOST_ALLOWLIST.equals(mode)) {
        List<String> hosts =
            requiredTextArray(ruleNode.get("allowedHosts"), "allowedHosts").stream()
                .map(this::normalizeAllowedHost)
                .distinct()
                .toList();
        if (hosts.isEmpty()) throw ctaError("HOST_ALLOWLIST requires allowedHosts");
        JsonNode subdomains = ruleNode.get("allowSubdomains");
        JsonNode https = ruleNode.get("requireHttps");
        if (subdomains == null || !subdomains.isBoolean() || https == null || !https.isBoolean()) {
          throw ctaError("HOST_ALLOWLIST requires allowSubdomains and requireHttps booleans");
        }
        JsonNode pathsNode = ruleNode.get("allowedPathPrefixes");
        if (pathsNode == null || !pathsNode.isArray()) {
          throw ctaError("HOST_ALLOWLIST requires allowedPathPrefixes");
        }
        List<String> paths = new ArrayList<>();
        for (JsonNode item : pathsNode) {
          if (!item.isTextual()) throw ctaError("Path prefixes must be strings");
          paths.add(normalizePathPrefix(item.textValue()));
        }
        result.put(
            key,
            new UrlRule(
                key,
                mode,
                List.of(),
                hosts,
                subdomains.booleanValue(),
                https.booleanValue(),
                List.copyOf(new LinkedHashSet<>(paths))));
      } else {
        throw ctaError("Unsupported URL rule mode");
      }
    }
    return result;
  }

  private Map<String, CallbackRule> parseCallbackRules(
      JsonNode rulesNode, Set<String> allowedKeys) {
    if (rulesNode == null || rulesNode.isNull()) {
      return Map.of();
    }
    if (!rulesNode.isArray()) {
      throw ctaError("callbackRules must be an array");
    }
    Map<String, CallbackRule> result = new LinkedHashMap<>();
    for (JsonNode ruleNode : rulesNode) {
      if (!ruleNode.isObject()) throw ctaError("Callback rule must be an object");
      String key = requiredText(ruleNode, "key");
      if (!allowedKeys.contains(key)) {
        throw ctaError("Callback rule references a non-callback variable");
      }
      if (result.containsKey(key)) {
        throw ctaError("Only one callback rule is allowed per variable");
      }
      List<String> allowedValues =
          requiredTextArray(ruleNode.get("allowedValues"), "allowedValues").stream()
              .map(this::normalizeCallback)
              .distinct()
              .toList();
      if (allowedValues.isEmpty()) throw ctaError("Callback rule requires allowedValues");
      result.put(key, new CallbackRule(key, allowedValues));
    }
    return result;
  }

  private boolean urlAllowed(UrlRule rule, String value) {
    try {
      CanonicalUrl candidate = parseUrl(value);
      if (EXACT_VALUES.equals(rule.mode())) {
        return rule.allowedValues().contains(candidate.canonical());
      }
      if (rule.requireHttps() && !"https".equals(candidate.scheme())) return false;
      if (!safeHostPort(candidate)) return false;
      boolean hostAllowed =
          rule.allowedHosts().stream()
              .anyMatch(
                  allowedHost ->
                      candidate.host().equals(allowedHost)
                          || (rule.allowSubdomains()
                              && candidate.host().endsWith("." + allowedHost)));
      if (!hostAllowed) return false;
      return rule.allowedPathPrefixes().isEmpty()
          || rule.allowedPathPrefixes().stream().anyMatch(candidate.path()::startsWith);
    } catch (BadRequestException exception) {
      return false;
    }
  }

  private boolean callbackAllowed(CallbackRule rule, String value) {
    try {
      return rule.allowedValues().contains(normalizeCallback(value));
    } catch (BadRequestException exception) {
      return false;
    }
  }

  private CanonicalUrl parseUrl(String value) {
    try {
      URI uri = URI.create(value.trim()).normalize();
      String scheme = uri.getScheme();
      if (scheme == null
          || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
        throw ctaError("CTA URL must use HTTP or HTTPS");
      }
      if (uri.getRawUserInfo() != null || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@"))) {
        throw ctaError("CTA URL user-info is forbidden");
      }
      HostPort hostPort = hostPort(uri);
      String host = normalizeHost(hostPort.host());
      if (isIpLiteral(host)) throw ctaError("CTA URL IP hosts are forbidden");
      String path = uri.getRawPath();
      if (path == null || path.isEmpty()) path = "/";
      String normalizedPath = URI.create(path).normalize().getRawPath();
      if (normalizedPath == null || normalizedPath.isEmpty()) normalizedPath = "/";
      String lowerScheme = scheme.toLowerCase(Locale.ROOT);
      int canonicalPort =
          ("https".equals(lowerScheme) && hostPort.port() == 443)
                  || ("http".equals(lowerScheme) && hostPort.port() == 80)
              ? -1
              : hostPort.port();
      StringBuilder canonical = new StringBuilder(lowerScheme).append("://").append(host);
      if (canonicalPort >= 0) canonical.append(':').append(canonicalPort);
      canonical.append(normalizedPath);
      if (uri.getRawQuery() != null) canonical.append('?').append(uri.getRawQuery());
      if (uri.getRawFragment() != null) canonical.append('#').append(uri.getRawFragment());
      return new CanonicalUrl(
          canonical.toString(), lowerScheme, host, normalizedPath, canonicalPort);
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ctaError("CTA URL is invalid");
    }
  }

  private HostPort hostPort(URI uri) {
    String host = uri.getHost();
    int port = uri.getPort();
    if (host != null) return new HostPort(host, port);

    String authority = uri.getRawAuthority();
    if (authority == null || authority.isBlank()) throw ctaError("CTA URL host is required");
    if (authority.startsWith("[") || authority.contains("]")) {
      throw ctaError("CTA URL IP hosts are forbidden");
    }
    int colon = authority.lastIndexOf(':');
    if (colon > 0) {
      String portText = authority.substring(colon + 1);
      if (!portText.matches("[0-9]{1,5}")) throw ctaError("CTA URL host is invalid");
      port = Integer.parseInt(portText);
      if (port > 65535) throw ctaError("CTA URL port is invalid");
      authority = authority.substring(0, colon);
    }
    return new HostPort(authority, port);
  }

  private boolean safeHostPort(CanonicalUrl url) {
    return url.port() < 0
        || ("https".equals(url.scheme()) && url.port() == 443)
        || ("http".equals(url.scheme()) && url.port() == 80);
  }

  private String normalizeAllowedHost(String value) {
    String host = normalizeHost(value);
    if (isIpLiteral(host) || host.contains(":") || host.contains("/") || host.contains("@")) {
      throw ctaError("Allowed CTA host is invalid");
    }
    return host;
  }

  private String normalizeHost(String value) {
    try {
      if (value == null || value.isBlank() || value.contains("%")) {
        throw ctaError("CTA URL host is invalid");
      }
      String normalized =
          IDN.toASCII(value.trim(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
      if (normalized.isBlank()
          || normalized.startsWith(".")
          || normalized.endsWith(".")
          || normalized.length() > 253) {
        throw ctaError("CTA URL host is invalid");
      }
      return normalized;
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ctaError("CTA URL host is invalid");
    }
  }

  private boolean isIpLiteral(String host) {
    if (host.contains(":")) return true;
    String[] parts = host.split("\\.", -1);
    if (parts.length != 4) return false;
    for (String part : parts) {
      if (!part.matches("[0-9]{1,3}")) return false;
      if (Integer.parseInt(part) > 255) return false;
    }
    return true;
  }

  private String normalizePathPrefix(String value) {
    if (value == null || value.isBlank() || !value.startsWith("/")) {
      throw ctaError("CTA path prefix must start with /");
    }
    if (value.contains("?") || value.contains("#")) {
      throw ctaError("CTA path prefix cannot contain query or fragment");
    }
    try {
      String normalized = URI.create(value).normalize().getRawPath();
      if (normalized == null || !normalized.startsWith("/")) {
        throw ctaError("CTA path prefix is invalid");
      }
      return normalized;
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ctaError("CTA path prefix is invalid");
    }
  }

  private String normalizeCallback(String value) {
    String raw = value == null ? "" : value.trim();
    if (!raw.matches("\\+?[0-9() .-]+")) {
      throw ctaError("CTA callback is invalid");
    }
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.length() < 6 || digits.length() > 15) {
      throw ctaError("CTA callback must normalize to 6-15 digits");
    }
    return digits;
  }

  private Set<String> keysOfType(List<VariableDefinition> variables, VariableType type) {
    Set<String> keys = new LinkedHashSet<>();
    for (VariableDefinition variable : variables) {
      if (variable.type() == type && !keys.add(variable.key())) {
        throw ctaError("Duplicate CTA variable key");
      }
    }
    return Set.copyOf(keys);
  }

  private String requiredText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw ctaError(field + " is required");
    }
    return value.textValue().trim();
  }

  private List<String> requiredTextArray(JsonNode node, String field) {
    if (node == null || !node.isArray()) throw ctaError(field + " must be an array");
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw ctaError(field + " must contain nonblank strings");
      }
      values.add(item.textValue().trim());
    }
    return values;
  }

  private Map<String, String> failAllCtaVariables(
      List<VariableDefinition> variables, String message) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (VariableDefinition variable : variables) {
      if (variable.type() == VariableType.URL || variable.type() == VariableType.CALLBACK) {
        errors.put(variable.key(), message);
      }
    }
    return Map.copyOf(errors);
  }

  private BadRequestException ctaError(String message) {
    return new BadRequestException("MANAGED_SMS_CTA_NOT_APPROVED: " + message);
  }

  private record ValidatedPolicy(
      Map<String, UrlRule> urlRules,
      Map<String, CallbackRule> callbackRules,
      ObjectNode normalized) {}

  private record UrlRule(
      String key,
      String mode,
      List<String> allowedValues,
      List<String> allowedHosts,
      boolean allowSubdomains,
      boolean requireHttps,
      List<String> allowedPathPrefixes) {}

  private record CallbackRule(String key, List<String> allowedValues) {}

  private record CanonicalUrl(
      String canonical, String scheme, String host, String path, int port) {}

  private record HostPort(String host, int port) {}
}
