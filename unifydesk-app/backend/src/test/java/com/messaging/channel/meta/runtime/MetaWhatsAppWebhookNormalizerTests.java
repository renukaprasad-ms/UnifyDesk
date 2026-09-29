package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetaWhatsAppWebhookNormalizerTests {

  private MetaWhatsAppWebhookNormalizer normalizer;

  @BeforeEach
  void setUp() {
    normalizer =
        new MetaWhatsAppWebhookNormalizer(
            new ObjectMapper(), new MetaWhatsAppRuntimeProperties());
  }

  @Test
  void duplicateMessagePayloadProducesStableEventKey() {
    byte[] payload = inboundText("wamid.duplicate").getBytes(StandardCharsets.UTF_8);

    var first = normalizer.normalize(payload);
    var second = normalizer.normalize(payload);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
    assertThat(first.getFirst().eventKey()).isEqualTo(second.getFirst().eventKey());
    assertThat(first.getFirst().eventKey()).isEqualTo("msg:phone-1:wamid.duplicate");
    assertThat(first.getFirst().eventKind()).isEqualTo("MESSAGE");
    assertThat(first.getFirst().phoneNumberId()).isEqualTo("phone-1");
    assertThat(first.getFirst().providerMessageId()).isEqualTo("wamid.duplicate");
  }

  @Test
  void textInboundRetainsOnlyNormalizedEffectContext() {
    var event =
        normalizer
            .normalize(inboundText("wamid.text").getBytes(StandardCharsets.UTF_8))
            .getFirst();

    assertThat(event.payloadJson()).contains("\"message\"");
    assertThat(event.payloadJson()).contains("\"contacts\"");
    assertThat(event.payloadJson()).contains("\"phoneNumberId\":\"phone-1\"");
    assertThat(event.payloadJson()).doesNotContain("access_token");
  }

  @Test
  void statusUsesFrozenCanonicalIdentity() {
    String payload =
        """
        {
          "object":"whatsapp_business_account",
          "entry":[{
            "id":"waba-1",
            "changes":[{
              "field":"messages",
              "value":{
                "metadata":{"phone_number_id":"phone-1"},
                "statuses":[{
                  "id":"wamid.status",
                  "status":"delivered",
                  "timestamp":"1700000001"
                }]
              }
            }]
          }]
        }
        """;

    var event = normalizer.normalize(payload.getBytes(StandardCharsets.UTF_8)).getFirst();

    assertThat(event.eventKey())
        .isEqualTo("status:phone-1:wamid.status:delivered:1700000001");
  }

  @Test
  void statusWithoutTimestampStillHasStableDeterministicFallbackKey() {
    String payload =
        """
        {
          "object":"whatsapp_business_account",
          "entry":[{
            "id":"waba-1",
            "changes":[{
              "field":"messages",
              "value":{
                "metadata":{"phone_number_id":"phone-1"},
                "statuses":[{"id":"wamid.status","status":"sent"}]
              }
            }]
          }]
        }
        """;
    byte[] body = payload.getBytes(StandardCharsets.UTF_8);

    String first = normalizer.normalize(body).getFirst().eventKey();
    String second = normalizer.normalize(body).getFirst().eventKey();

    assertThat(first).isEqualTo(second);
    assertThat(first).startsWith("sha256:");
  }

  @Test
  void malformedJsonIsRejectedAfterSignatureLayer() {
    assertThatThrownBy(
            () -> normalizer.normalize("{bad".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(MetaWhatsAppWebhookNormalizer.InvalidPayloadException.class);
  }

  @Test
  void unsupportedMessageTypeStillNormalizesAsMessageEffect() {
    String payload =
        """
        {
          "object":"whatsapp_business_account",
          "entry":[{
            "id":"waba-1",
            "changes":[{
              "field":"messages",
              "value":{
                "metadata":{"phone_number_id":"phone-1"},
                "messages":[{
                  "id":"wamid.sticker",
                  "from":"15550001111",
                  "timestamp":"1700000000",
                  "type":"sticker",
                  "sticker":{"id":"media-1"}
                }]
              }
            }]
          }]
        }
        """;

    var events = normalizer.normalize(payload.getBytes(StandardCharsets.UTF_8));

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().eventKind()).isEqualTo("MESSAGE");
  }

  @Test
  void unknownSignedChangeIsNormalizedAsUnknownForSafeIgnore() {
    String payload =
        """
        {
          "object":"whatsapp_business_account",
          "entry":[{
            "id":"waba-1",
            "changes":[{
              "field":"future_unknown_field",
              "value":{"metadata":{"phone_number_id":"phone-1"},"foo":"bar"}
            }]
          }]
        }
        """;

    var events = normalizer.normalize(payload.getBytes(StandardCharsets.UTF_8));

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().eventKind()).isEqualTo("UNKNOWN");
  }

  private String inboundText(String id) {
    return """
        {
          "object":"whatsapp_business_account",
          "entry":[{
            "id":"waba-1",
            "changes":[{
              "field":"messages",
              "value":{
                "metadata":{"phone_number_id":"phone-1"},
                "contacts":[{"wa_id":"15550001111","profile":{"name":"Customer"}}],
                "messages":[{
                  "id":"%s",
                  "from":"15550001111",
                  "timestamp":"1700000000",
                  "type":"text",
                  "text":{"body":"hello"}
                }]
              }
            }]
          }]
        }
        """.formatted(id);
  }
}
