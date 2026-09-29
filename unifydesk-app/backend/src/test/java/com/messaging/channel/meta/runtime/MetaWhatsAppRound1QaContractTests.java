package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookNormalizer.InvalidPayloadException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetaWhatsAppRound1QaContractTests {

  @Test
  void rejectsNonWhatsAppTopLevelWebhookEnvelope() {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    MetaWhatsAppWebhookNormalizer normalizer =
        new MetaWhatsAppWebhookNormalizer(new ObjectMapper(), properties);

    byte[] body =
        """
        {"object":"page","entry":[]}
        """
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> normalizer.normalize(body))
        .isInstanceOf(InvalidPayloadException.class);
  }

  @Test
  void rejectsWrongChangeFieldEvenWhenPayloadLooksLikeWhatsAppMessages() {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    MetaWhatsAppWebhookNormalizer normalizer =
        new MetaWhatsAppWebhookNormalizer(new ObjectMapper(), properties);

    byte[] body =
        """
        {
          "object":"whatsapp_business_account",
          "entry":[{
            "id":"waba-1",
            "changes":[{
              "field":"feed",
              "value":{
                "metadata":{"phone_number_id":"phone-1"},
                "messages":[{
                  "id":"wamid.1",
                  "from":"15550001111",
                  "timestamp":"1790636400",
                  "type":"text",
                  "text":{"body":"hello"}
                }]
              }
            }]
          }]
        }
        """
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> normalizer.normalize(body))
        .isInstanceOf(InvalidPayloadException.class);
  }

  @Test
  void olderFailureCannotOverwriteNewerAcceptedState() {
    MetaWhatsAppMessageStateMachine stateMachine = new MetaWhatsAppMessageStateMachine();
    MetaWhatsAppMessageStateMachine.State failed = stateMachine.providerState("failed");

    boolean shouldApply =
        stateMachine.shouldApply(
            "ACCEPTED",
            10,
            Instant.parse("2026-09-29T10:00:00Z"),
            failed,
            Instant.parse("2026-09-29T09:59:59Z"));

    assertThat(shouldApply).isFalse();
  }
}
