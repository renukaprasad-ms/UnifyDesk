package com.messaging.broadcast.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.channel.enums.ChannelType;
import org.junit.jupiter.api.Test;

class BroadcastTemplateRendererTests {

  private final BroadcastTemplateRenderer renderer =
      new BroadcastTemplateRenderer(new ObjectMapper());

  @Test
  void rendersBuiltInAndMetadataVariables() {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setDestination("renuka@example.com");
    recipient.setDisplayName("Renuka Prasad");
    recipient.setPersonalizationJson(
        """
        {"discount":"20","expiry_date":"30 September","city":"Bengaluru"}
        """);

    BroadcastTemplateRenderer.RenderedContent result =
        renderer.render(
            "Hi {{first_name}}",
            "Get {{discount}}% off in {{city}} until {{expiry_date}}. Email: {{email}}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("Hi Renuka", result.subject());
    assertEquals(
        "Get 20% off in Bengaluru until 30 September. Email: renuka@example.com",
        result.bodyText());
    assertTrue(result.unresolvedVariables().isEmpty());
  }

  @Test
  void normalizesCsvHeaderNamesForVariables() {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setDestination("renuka@example.com");
    recipient.setPersonalizationJson(
        """
        {"First Name":"Renuka","Offer URL":"https://example.com"}
        """);

    BroadcastTemplateRenderer.RenderedContent result =
        renderer.render(
            null,
            "Hi {{first_name}}, visit {{offer_url}}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("Hi Renuka, visit https://example.com", result.bodyText());
    assertTrue(result.unresolvedVariables().isEmpty());
  }

  @Test
  void reportsMissingVariablesWithoutRemovingToken() {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setDestination("renuka@example.com");

    BroadcastTemplateRenderer.RenderedContent result =
        renderer.render(
            null,
            "Hi {{first_name}}, use code {{coupon_code}}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("Hi {{first_name}}, use code {{coupon_code}}", result.bodyText());
    assertEquals(2, result.unresolvedVariables().size());
    assertTrue(result.unresolvedVariables().contains("first_name"));
    assertTrue(result.unresolvedVariables().contains("coupon_code"));
  }
}
