package com.messaging.contacts.processing;

public final class CsvSafety {

  private CsvSafety() {}

  public static String neutralizeFormula(String value) {
    if (value == null || value.isEmpty()) return value;
    int index = 0;
    while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
    if (index < value.length()) {
      char first = value.charAt(index);
      if (first == '=' || first == '+' || first == '-' || first == '@') {
        return "'" + value;
      }
    }
    return value;
  }

  public static void validateCell(String value, int maxChars) {
    if (value == null) return;
    if (value.length() > maxChars) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_IMPORT_INVALID");
    }
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == 0 || (Character.isISOControl(ch) && ch != '\t' && ch != '\r' && ch != '\n')) {
        throw new com.messaging.common.exception.BadRequestException("CONTACT_IMPORT_INVALID");
      }
    }
  }
}
