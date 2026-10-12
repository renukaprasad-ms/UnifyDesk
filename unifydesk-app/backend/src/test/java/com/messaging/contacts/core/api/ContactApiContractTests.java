package com.messaging.contacts.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.core.api.ContactDtos.ContactDashboardSummary;
import com.messaging.contacts.core.api.ContactDtos.ContactDetail;
import com.messaging.contacts.core.api.ContactDtos.ContactSummary;
import com.messaging.contacts.core.api.ContactDtos.CustomFieldApiType;
import com.messaging.contacts.core.api.ContactDtos.CustomFieldDefinitionResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContactApiContractTests {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void customFieldDefinitionUsesExplicitPublicContractAndStringId() {
    CustomFieldDefinitionResponse response =
        new CustomFieldDefinitionResponse(
            "922337203685470001",
            "customer_tier",
            "Customer tier",
            CustomFieldApiType.SINGLE_SELECT,
            List.of("STANDARD", "PREMIUM"),
            "EXACT",
            "ACTIVE",
            10,
            null,
            null);

    JsonNode json = objectMapper.valueToTree(response);

    assertThat(json.get("id").isTextual()).isTrue();
    assertThat(json.get("id").asText()).isEqualTo("922337203685470001");
    assertThat(json.get("label").asText()).isEqualTo("Customer tier");
    assertThat(json.get("type").asText()).isEqualTo("SINGLE_SELECT");
    assertThat(json.get("options").isArray()).isTrue();
    assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(json.get("indexability").asText()).isEqualTo("EXACT");
  }

  @Test
  void contactSummaryExposesMarketingPreferenceSummary() {
    List<String> componentNames =
        Arrays.stream(ContactSummary.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames).contains("preferenceSummary");
    assertThat(componentType(ContactSummary.class, "preferenceSummary")).isEqualTo(java.util.Map.class);
  }

  @Test
  void dashboardSummaryExposesExactContactAndSubscriptionMetrics() {
    List<String> componentNames =
        Arrays.stream(ContactDashboardSummary.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames)
        .contains(
            "totalContacts",
            "newContacts30d",
            "emailSubscribers",
            "smsSubscribers",
            "whatsappSubscribers",
            "growth");
  }

  @Test
  void contactDetailExposesPrimaryDestinationsAndListMemberships() {
    List<String> componentNames =
        Arrays.stream(ContactDetail.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames)
        .contains("primaryEmail", "primaryPhone", "lists");

    assertThat(componentType(ContactDetail.class, "id")).isEqualTo(String.class);
    assertThat(componentType(ContactDetail.class, "companyId")).isEqualTo(String.class);
    assertThat(componentType(ContactDetail.class, "mergedIntoContactId")).isEqualTo(String.class);
    assertThat(componentType(ContactDetail.class, "ownerUserId")).isEqualTo(String.class);
  }

  private Class<?> componentType(Class<?> recordType, String name) {
    return Arrays.stream(recordType.getRecordComponents())
        .filter(component -> component.getName().equals(name))
        .findFirst()
        .orElseThrow()
        .getType();
  }
}
