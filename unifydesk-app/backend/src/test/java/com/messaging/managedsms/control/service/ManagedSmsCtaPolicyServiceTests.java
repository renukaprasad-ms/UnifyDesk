package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.VariableDefinition;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.VariableType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagedSmsCtaPolicyServiceTests {

  private ObjectMapper objectMapper;
  private ManagedSmsJsonSupport jsonSupport;
  private ManagedSmsCtaPolicyService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    jsonSupport = new ManagedSmsJsonSupport(objectMapper);
    service = new ManagedSmsCtaPolicyService(objectMapper, jsonSupport);
  }

  @Test
  void exactUrlPolicyAllowsOnlyCanonicalApprovedValue() throws Exception {
    List<VariableDefinition> schema = urlSchema();
    String policy =
        service.finalizeForActivation(
            jsonSupport.variables(schema),
            json(
                """
                {
                  "policyVersion":2,
                  "urlRules":[{
                    "key":"tracking_url",
                    "mode":"EXACT_VALUES",
                    "allowedValues":["https://Example.COM/orders/a"]
                  }],
                  "callbackRules":[]
                }
                """));

    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://example.com/orders/a")))
        .isEmpty();
    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://evil.example/orders/a")))
        .containsKey("tracking_url");
  }

  @Test
  void hostAllowlistCanRequireExactHost() throws Exception {
    List<VariableDefinition> schema = urlSchema();
    String policy =
        hostPolicy(schema, false, true, List.of("/orders/"));

    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://example.com/orders/123")))
        .isEmpty();
    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://sub.example.com/orders/123")))
        .containsKey("tracking_url");
  }

  @Test
  void hostAllowlistAllowsOnlyLabelBoundedSubdomainsWhenEnabled() throws Exception {
    List<VariableDefinition> schema = urlSchema();
    String policy =
        hostPolicy(schema, true, true, List.of("/orders/"));

    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://a.example.com/orders/123")))
        .isEmpty();
    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://notexample.com/orders/123")))
        .containsKey("tracking_url");
  }

  @Test
  void hostAllowlistEnforcesHttpsAndPathPrefix() throws Exception {
    List<VariableDefinition> schema = urlSchema();
    String policy =
        hostPolicy(schema, false, true, List.of("/orders/"));

    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "http://example.com/orders/123")))
        .containsKey("tracking_url");
    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://example.com/admin")))
        .containsKey("tracking_url");
    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("tracking_url", "https://example.com/orders/123")))
        .isEmpty();
  }

  @Test
  void ipLiteralAndUserInfoUrlsAreRejectedAtApproval() throws Exception {
    List<VariableDefinition> schema = urlSchema();

    assertThatThrownBy(
            () ->
                service.finalizeForActivation(
                    jsonSupport.variables(schema),
                    exactPolicy("https://127.0.0.1/orders/a")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");

    assertThatThrownBy(
            () ->
                service.finalizeForActivation(
                    jsonSupport.variables(schema),
                    exactPolicy("https://user@example.com/orders/a")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  @Test
  void callbackPolicyNormalizesThenMatchesExactAllowlist() throws Exception {
    List<VariableDefinition> schema =
        List.of(new VariableDefinition("support_phone", VariableType.CALLBACK, 20));
    String policy =
        service.finalizeForActivation(
            jsonSupport.variables(schema),
            json(
                """
                {
                  "policyVersion":2,
                  "urlRules":[],
                  "callbackRules":[{
                    "key":"support_phone",
                    "allowedValues":["+91 98765 43210"]
                  }]
                }
                """));

    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("support_phone", "919876543210")))
        .isEmpty();
    assertThat(
            service.validateRuntimeValues(
                policy, schema, Map.of("support_phone", "919999999999")))
        .containsKey("support_phone");
  }

  @Test
  void missingAuthoritativeRuleBlocksActivation() {
    List<VariableDefinition> schema = urlSchema();

    assertThatThrownBy(
            () ->
                service.finalizeForActivation(
                    jsonSupport.variables(schema), objectMapper.createObjectNode()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("policyVersion=2");

    assertThatThrownBy(
            () ->
                service.finalizeForActivation(
                    jsonSupport.variables(schema),
                    objectMapper
                        .createObjectNode()
                        .put("policyVersion", 2)
                        .set("urlRules", objectMapper.createArrayNode())))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Every URL variable");
  }

  private String hostPolicy(
      List<VariableDefinition> schema,
      boolean allowSubdomains,
      boolean requireHttps,
      List<String> pathPrefixes)
      throws Exception {
    var root = objectMapper.createObjectNode();
    root.put("policyVersion", 2);
    var rules = root.putArray("urlRules");
    var rule = rules.addObject();
    rule.put("key", "tracking_url");
    rule.put("mode", "HOST_ALLOWLIST");
    rule.putArray("allowedHosts").add("Example.COM");
    rule.put("allowSubdomains", allowSubdomains);
    rule.put("requireHttps", requireHttps);
    var paths = rule.putArray("allowedPathPrefixes");
    pathPrefixes.forEach(paths::add);
    root.putArray("callbackRules");
    return service.finalizeForActivation(jsonSupport.variables(schema), root);
  }

  private JsonNode exactPolicy(String value) {
    var root = objectMapper.createObjectNode();
    root.put("policyVersion", 2);
    var rule = root.putArray("urlRules").addObject();
    rule.put("key", "tracking_url");
    rule.put("mode", "EXACT_VALUES");
    rule.putArray("allowedValues").add(value);
    root.putArray("callbackRules");
    return root;
  }

  private List<VariableDefinition> urlSchema() {
    return List.of(new VariableDefinition("tracking_url", VariableType.URL, 300));
  }

  private JsonNode json(String value) throws Exception {
    return objectMapper.readTree(value);
  }
}
