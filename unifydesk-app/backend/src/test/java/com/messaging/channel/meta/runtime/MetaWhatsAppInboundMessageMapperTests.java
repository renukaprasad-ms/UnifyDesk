package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MetaWhatsAppInboundMessageMapperTests {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MetaWhatsAppInboundMessageMapper mapper =
      new MetaWhatsAppInboundMessageMapper();

  @Test
  void mapsInboundTextAndRetainsMissingParentProviderContext() throws Exception {
    var message =
        objectMapper.readTree(
            """
            {
              "id":"wamid.child",
              "type":"text",
              "context":{"id":"wamid.parent-not-local"},
              "text":{"body":"hello"}
            }
            """);

    var mapped = mapper.map(message);

    assertThat(mapped.kind()).isEqualTo("TEXT");
    assertThat(mapped.bodyText()).isEqualTo("hello");
    assertThat(mapped.replyToProviderMessageId()).isEqualTo("wamid.parent-not-local");
  }

  @Test
  void mapsImageAndPdfDocumentMediaMetadata() throws Exception {
    var image =
        mapper.map(
            objectMapper.readTree(
                """
                {
                  "type":"image",
                  "image":{"id":"media-image","mime_type":"image/jpeg","caption":"photo"}
                }
                """));
    var pdf =
        mapper.map(
            objectMapper.readTree(
                """
                {
                  "type":"document",
                  "document":{
                    "id":"media-pdf",
                    "mime_type":"application/pdf",
                    "filename":"invoice.pdf"
                  }
                }
                """));

    assertThat(image.kind()).isEqualTo("IMAGE");
    assertThat(image.providerMediaId()).isEqualTo("media-image");
    assertThat(pdf.kind()).isEqualTo("DOCUMENT");
    assertThat(pdf.mimeType()).isEqualTo("application/pdf");
    assertThat(pdf.fileName()).isEqualTo("invoice.pdf");
  }

  @Test
  void unsupportedMessageGetsVisiblePlaceholderInsteadOfBeingDropped() throws Exception {
    var mapped =
        mapper.map(
            objectMapper.readTree(
                """
                {"type":"sticker","sticker":{"id":"media-sticker"}}
                """));

    assertThat(mapped.kind()).isEqualTo("UNSUPPORTED");
    assertThat(mapped.bodyText()).contains("Unsupported WhatsApp message: sticker");
    assertThat(mapped.providerMediaId()).isNull();
  }
}
