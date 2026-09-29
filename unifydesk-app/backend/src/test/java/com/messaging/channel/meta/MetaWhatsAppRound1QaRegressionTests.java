package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.meta.runtime.MetaWhatsAppRuntimeProperties;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookNormalizer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MetaWhatsAppRound1QaRegressionTests {

  @Test
  void rejectsUnsupportedSignedWebhookEnvelopeBeforeDurableAcceptance() {
    MetaWhatsAppWebhookNormalizer normalizer =
        new MetaWhatsAppWebhookNormalizer(
            new ObjectMapper(), new MetaWhatsAppRuntimeProperties());

    byte[] body =
        """
        {
          "object":"instagram",
          "entry":[]
        }
        """.getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> normalizer.normalize(body))
        .isInstanceOf(MetaWhatsAppWebhookNormalizer.InvalidPayloadException.class);
  }
}
