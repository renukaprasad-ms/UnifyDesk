package com.messaging.managedsms.control.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.VariableDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagedSmsJsonSupport {
  private final ObjectMapper objectMapper;

  public String json(JsonNode value, String emptyJson) {
    if (value == null || value.isNull()) return emptyJson;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid JSON value", exception);
    }
  }

  public String value(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid JSON value", exception);
    }
  }

  public boolean sameJson(String storedJson, JsonNode candidate) {
    try {
      JsonNode stored =
          storedJson == null || storedJson.isBlank()
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(storedJson);
      JsonNode requested = candidate == null || candidate.isNull() ? objectMapper.createObjectNode() : candidate;
      return stored.equals(requested);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored Managed SMS JSON is invalid", exception);
    }
  }

  public String variables(List<VariableDefinition> variables) {
    try {
      return objectMapper.writeValueAsString(variables == null ? List.of() : variables);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid variable schema", exception);
    }
  }

  public List<VariableDefinition> variables(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<VariableDefinition>>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("Stored Managed SMS variable schema is invalid", exception);
    }
  }
}
