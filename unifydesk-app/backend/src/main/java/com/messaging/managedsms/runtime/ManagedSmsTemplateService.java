package com.messaging.managedsms.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsTemplateService {

  private static final Pattern NUMERIC = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
  private static final Pattern CALLBACK = Pattern.compile("\\+?[0-9]{8,15}");
  private static final Pattern EMAIL =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern ALPHANUMERIC =
      Pattern.compile("[\\p{L}\\p{N} .,_\\-/:@()+]+");

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
    return new DraftTemplate(runtime.templateId(), runtime.versionNo(), rendered.body(), rendered.variables());
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
    if (expectedFingerprint == null || expectedFingerprint.isBlank()) {
      throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
    }
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
    validateCtaPolicy(ctaPolicyJson, specs);
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
      URI uri = URI.create(value);
      return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null
          && uri.getUserInfo() == null;
    } catch (RuntimeException exception) {
      return false;
    }
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

  private void validateCtaPolicy(String ctaPolicyJson, List<VariableSpec> specs) {
    if (ctaPolicyJson == null || ctaPolicyJson.isBlank()) return;
    try {
      JsonNode policy = objectMapper.readTree(ctaPolicyJson);
      Map<String, String> types = new HashMap<>();
      for (VariableSpec spec : specs) types.put(spec.key(), spec.type());
      for (JsonNode key : policy.path("urlVariables")) {
        if (!"URL".equals(types.get(key.asText()))) {
          throw new BadRequestException("MANAGED_SMS_CTA_NOT_APPROVED");
        }
      }
      for (JsonNode key : policy.path("callbackVariables")) {
        String type = types.get(key.asText());
        if (!"CALLBACK".equals(type) && !"PHONE".equals(type)) {
          throw new BadRequestException("MANAGED_SMS_CTA_NOT_APPROVED");
        }
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_CTA_NOT_APPROVED");
    }
  }


  public record VariableSpec(String key, String type, int maxLength, String providerKey) {}

  public record RenderedTemplate(
      String body, Map<String, String> variables, List<VariableSpec> variableSpecs) {}

  public record DraftTemplate(
      Long templateId, Long versionNo, String body, Map<String, String> variables) {}
}
