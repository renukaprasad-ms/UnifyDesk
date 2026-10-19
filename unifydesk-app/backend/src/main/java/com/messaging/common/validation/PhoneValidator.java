package com.messaging.common.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
  private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) return true;
    String raw = value.trim();
    if (!raw.matches("\\+?[0-9 ()-]+")) return false;
    try {
      var number = PHONES.parse(raw, "IN");
      String digits = raw.replaceAll("\\D", "");
      int nationalLength = raw.startsWith("+91") ? digits.length() - 2 : digits.length();
      if (number.getCountryCode() == 91 && nationalLength != 10) {
        return false;
      }
      return PHONES.isPossibleNumber(number) && PHONES.isValidNumber(number);
    } catch (NumberParseException exception) {
      return false;
    }
  }
}
