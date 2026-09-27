package com.messaging.managedsms.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedSmsTemplateServiceTests {

  private final ManagedSmsTemplateService service =
      new ManagedSmsTemplateService(mock(ManagedSmsRuntimeRepository.class), new ObjectMapper());

  @Test
  void rendersOnlyDeclaredVariablesIntoApprovedBody() {
    ManagedSmsTemplateService.RenderedTemplate rendered =
        service.render(
            "Your order {#var#} has shipped",
            "opaque-fingerprint",
            "[{\"key\":\"order_id\",\"type\":\"ALPHANUMERIC\",\"maxLength\":20}]",
            "{}",
            Map.of("order_id", "A123"));

    assertThat(rendered.body()).isEqualTo("Your order A123 has shipped");
    assertThat(rendered.variables()).containsExactlyEntriesOf(Map.of("order_id", "A123"));
  }

  @Test
  void rejectsExtraVariablesAndPlaceholderInjection() {
    assertThatThrownBy(
            () ->
                service.render(
                    "Code {#var#}",
                    "opaque-fingerprint",
                    "[{\"key\":\"code\",\"type\":\"ALPHANUMERIC\",\"maxLength\":20}]",
                    "{}",
                    Map.of("code", "123", "extra", "ignored")))
        .isInstanceOf(BadRequestException.class);

    assertThatThrownBy(
            () ->
                service.render(
                    "Code {#var#}",
                    "opaque-fingerprint",
                    "[{\"key\":\"code\",\"type\":\"TEXT\",\"maxLength\":20}]",
                    "{}",
                    Map.of("code", "{{evil}}")))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void rejectsInvalidUrlForUrlTaggedVariable() {
    assertThatThrownBy(
            () ->
                service.render(
                    "Open {#var#}",
                    "opaque-fingerprint",
                    "[{\"key\":\"link\",\"type\":\"URL\",\"maxLength\":100}]",
                    "{\"urlVariables\":[\"link\"]}",
                    Map.of("link", "javascript:alert(1)")))
        .isInstanceOf(BadRequestException.class);
  }
}
