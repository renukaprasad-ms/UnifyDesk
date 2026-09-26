package com.messaging.managedemail.events;

public enum ManagedEmailProviderEventType {
  SEND,
  DELIVERY,
  BOUNCE,
  COMPLAINT,
  REJECT,
  OPEN,
  CLICK,
  DELIVERY_DELAY,
  RENDERING_FAILURE,
  SUBSCRIPTION;

  public static ManagedEmailProviderEventType fromSes(String value) {
    if (value == null) throw new IllegalArgumentException("Missing SES event type");
    return switch (value.trim().toUpperCase()) {
      case "SEND" -> SEND;
      case "DELIVERY" -> DELIVERY;
      case "BOUNCE" -> BOUNCE;
      case "COMPLAINT" -> COMPLAINT;
      case "REJECT" -> REJECT;
      case "OPEN" -> OPEN;
      case "CLICK" -> CLICK;
      case "DELIVERYDELAY", "DELIVERY_DELAY" -> DELIVERY_DELAY;
      case "RENDERINGFAILURE", "RENDERING_FAILURE" -> RENDERING_FAILURE;
      default -> throw new IllegalArgumentException("Unsupported SES event type");
    };
  }
}
