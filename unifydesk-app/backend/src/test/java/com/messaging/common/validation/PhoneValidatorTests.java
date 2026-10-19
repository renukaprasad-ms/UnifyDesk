package com.messaging.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneValidatorTests {
  private final PhoneValidator validator = new PhoneValidator();

  @ParameterizedTest
  @ValueSource(strings = {"+919876543210", "9876543210", "+12025550123", "+4915123456789", ""})
  void acceptsCountrySpecificValidNumbers(String value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"+91987654321", "+9198765432101", "+4915", "invalid", "+910000000000"})
  void rejectsInvalidNumbersAndIncorrectIndianLengths(String value) {
    assertThat(validator.isValid(value, null)).isFalse();
  }
}
