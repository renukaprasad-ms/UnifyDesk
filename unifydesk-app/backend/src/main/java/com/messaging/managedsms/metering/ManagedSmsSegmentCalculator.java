package com.messaging.managedsms.metering;

import org.springframework.stereotype.Component;

@Component
public class ManagedSmsSegmentCalculator {

  public int segments(String body) {
    if (body == null || body.isEmpty()) return 0;
    boolean gsm = body.chars().allMatch(this::isBasicGsmCharacter);
    int length = body.codePointCount(0, body.length());
    if (gsm) {
      if (length <= 160) return 1;
      return (length + 152) / 153;
    }
    if (length <= 70) return 1;
    return (length + 66) / 67;
  }

  private boolean isBasicGsmCharacter(int value) {
    return value == 10
        || value == 13
        || (value >= 32 && value <= 126)
        || value == 163
        || value == 165
        || value == 232
        || value == 233
        || value == 249;
  }
}
