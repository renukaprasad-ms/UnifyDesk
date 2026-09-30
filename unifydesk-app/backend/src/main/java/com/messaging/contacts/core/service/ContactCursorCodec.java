package com.messaging.contacts.core.service;

import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.exception.ContactCoreException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContactCursorCodec {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] signingKey;

  public ContactCursorCodec(
      @Value("${APP_CONTACTS_CURSOR_HMAC_KEY:}") String contactsCursorKey,
      @Value("${CHANNEL_CREDENTIAL_MASTER_KEY:}") String sharedPlatformKey) {
    String configuredKey = resolveSigningKey(contactsCursorKey, sharedPlatformKey);
    this.signingKey =
        MessageDigestHolder.sha256(configuredKey.getBytes(StandardCharsets.UTF_8));
  }

  private static String resolveSigningKey(
      String contactsCursorKey, String sharedPlatformKey) {
    if (contactsCursorKey != null && !contactsCursorKey.isBlank()) {
      return contactsCursorKey;
    }
    if (sharedPlatformKey != null && !sharedPlatformKey.isBlank()) {
      return sharedPlatformKey;
    }
    throw new IllegalStateException(
        "Contacts cursor signing key is not configured; set "
            + "APP_CONTACTS_CURSOR_HMAC_KEY or CHANNEL_CREDENTIAL_MASTER_KEY");
  }

  public String encode(
      long companyId, ContactSort sort, String filterHash, String sortValue, long contactId) {
    String encodedSortValue =
        ENCODER.encodeToString(
            (sortValue == null ? "" : sortValue).getBytes(StandardCharsets.UTF_8));
    String payload =
        "1\n"
            + companyId
            + "\n"
            + sort.name()
            + "\n"
            + filterHash
            + "\n"
            + encodedSortValue
            + "\n"
            + contactId;
    byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
    return ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(sign(payloadBytes));
  }

  public Decoded decode(
      String value, long companyId, ContactSort sort, String filterHash) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      String[] parts = value.split("\\.", -1);
      if (parts.length != 2) {
        throw ContactCoreException.invalidCursor();
      }
      byte[] payloadBytes = DECODER.decode(parts[0]);
      byte[] suppliedSignature = DECODER.decode(parts[1]);
      if (!MessageDigest.isEqual(sign(payloadBytes), suppliedSignature)) {
        throw ContactCoreException.invalidCursor();
      }

      String[] fields =
          new String(payloadBytes, StandardCharsets.UTF_8).split("\n", -1);
      if (fields.length != 6
          || !"1".equals(fields[0])
          || Long.parseLong(fields[1]) != companyId
          || !sort.name().equals(fields[2])
          || !filterHash.equals(fields[3])) {
        throw ContactCoreException.invalidCursor();
      }

      String sortValue =
          new String(DECODER.decode(fields[4]), StandardCharsets.UTF_8);
      return new Decoded(sortValue, Long.parseLong(fields[5]));
    } catch (ContactCoreException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ContactCoreException.invalidCursor();
    }
  }

  private byte[] sign(byte[] payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
      return mac.doFinal(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to sign contact cursor", exception);
    }
  }

  public record Decoded(String sortValue, long contactId) {}

  private static final class MessageDigestHolder {
    private MessageDigestHolder() {}

    private static byte[] sha256(byte[] value) {
      try {
        return MessageDigest.getInstance("SHA-256").digest(value);
      } catch (Exception exception) {
        throw new IllegalStateException("SHA-256 unavailable", exception);
      }
    }
  }
}
