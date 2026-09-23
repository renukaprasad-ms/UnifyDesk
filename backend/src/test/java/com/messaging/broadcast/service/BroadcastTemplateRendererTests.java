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
            null,
            "{}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("Hi Renuka", result.subject());
    assertEquals(
        "Get 20% off in Bengaluru until 30 September. Email: renuka@example.com",
        result.bodyText());
    assertTrue(result.unresolvedVariables().isEmpty());
    assertEquals("Renuka", result.templateVariables().get("first_name"));
    assertEquals("20", result.templateVariables().get("discount"));
    assertEquals("Bengaluru", result.templateVariables().get("city"));
    assertEquals("30 September", result.templateVariables().get("expiry_date"));
    assertEquals("renuka@example.com", result.templateVariables().get("email"));
  }

  @Test
  void escapesHtmlValuesButKeepsProviderVariablesRaw() {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setDestination("renuka@example.com");
    recipient.setPersonalizationJson(
        """
        {"cta_text":"<strong>Unsafe</strong>"}
        """);

    BroadcastTemplateRenderer.RenderedContent result =
        renderer.render(
            "Hello",
            "CTA: {{cta_text}}",
            "<p>{{cta_text}}</p>",
            "{}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("<p>&lt;strong&gt;Unsafe&lt;/strong&gt;</p>", result.bodyHtml());
    assertEquals("<strong>Unsafe</strong>", result.templateVariables().get("cta_text"));
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
            null,
            "{}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("Hi Renuka, visit https://example.com", result.bodyText());
    assertTrue(result.unresolvedVariables().isEmpty());
  }

  @Test
  void recipientMetadataOverridesBroadcastFallbackVariables() {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setDestination("renuka@example.com");
    recipient.setDisplayName("Renuka Prasad");
    recipient.setPersonalizationJson(
        """
        {"getting_started_url":"https://recipient.example.com/start"}
        """);

    BroadcastTemplateRenderer.RenderedContent result =
        renderer.render(
            "Welcome {{first_name}}",
            "Start at {{getting_started_url}}",
            null,
            """
            {"first_name":"Fallback","getting_started_url":"https://default.example.com/start"}
            """,
            recipient,
            ChannelType.EMAIL);

    assertEquals("Welcome Renuka", result.subject());
    assertEquals("Start at https://recipient.example.com/start", result.bodyText());
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
            null,
            "{}",
            recipient,
            ChannelType.EMAIL);

    assertEquals("Hi {{first_name}}, use code {{coupon_code}}", result.bodyText());
    assertEquals(2, result.unresolvedVariables().size());
    assertTrue(result.unresolvedVariables().contains("first_name"));
    assertTrue(result.unresolvedVariables().contains("coupon_code"));
  }
}
