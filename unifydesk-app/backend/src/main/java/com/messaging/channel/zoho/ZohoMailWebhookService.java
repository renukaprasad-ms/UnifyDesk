package com.messaging.channel.zoho;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zoho Mail outgoing webhooks are configured manually by each mailbox owner.
 * The high-entropy URL token binds the first signed handshake to a connection.
 * Zoho's X-Hook-Secret is delivered only on that first request; it is encrypted
 * at rest and subsequent requests must verify against that pinned secret.
 * The raw webhook payload is never trusted as mailbox data.
 */
@Service
@RequiredArgsConstructor
public class ZohoMailWebhookService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MAX_PAYLOAD_BYTES = 128 * 1024;

  private final JdbcTemplate jdbc;
  private final ChannelConnectionRepository connections;
  private final ZohoMailConnectionLookup connectionLookup;
  private final ZohoMailOAuthProperties properties;
  private final CredentialCipher cipher;
  private final ObjectMapper objectMapper;

  @Transactional
  public Setup create(Long companyId, Long connectionId) {
    requireEnabled();
    connectionLookup.requireActive(companyId, connectionId);
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    jdbc.update(
        """
        insert into zoho_mail_webhook_registrations(
            connection_id, company_id, token_sha256, status, updated_at)
        values (?, ?, ?, 'AWAITING_HANDSHAKE', now())
        on conflict (connection_id) do update set
            company_id=excluded.company_id,
            token_sha256=excluded.token_sha256,
            signing_secret_encrypted=null,
            status='AWAITING_HANDSHAKE',
            pending_at=null,
            last_received_at=null,
            last_processed_at=null,
            next_attempt_at=now(),
            lease_until=null,
            attempts=0,
            updated_at=now()
        """,
        connectionId,
        companyId,
        sha256(token));
    String base = validatedWebhookBase();
    String url =
        base + "/api/v1/webhooks/zoho-mail/" + connectionId + "?token=" + token;
    return new Setup(url, "AWAITING_HANDSHAKE");
  }

  @Transactional(readOnly = true)
  public Status status(Long companyId, Long connectionId) {
    connectionLookup.requireConnection(companyId, connectionId);
    List<Status> rows =
        jdbc.query(
            """
            select status, last_received_at, last_processed_at
            from zoho_mail_webhook_registrations
            where connection_id=? and company_id=?
            """,
            (rs, row) ->
                new Status(
                    true,
                    rs.getString("status"),
                    rs.getTimestamp("last_received_at") == null
                        ? null : rs.getTimestamp("last_received_at").toInstant(),
                    rs.getTimestamp("last_processed_at") == null
                        ? null : rs.getTimestamp("last_processed_at").toInstant()),
            connectionId,
            companyId);
    return rows.isEmpty() ? new Status(properties.isWebhookEnabled(), "NOT_CONFIGURED", null, null)
        : rows.getFirst();
  }

  @Transactional
  public void disable(Long companyId, Long connectionId) {
    connectionLookup.requireConnection(companyId, connectionId);
    jdbc.update(
        "delete from zoho_mail_webhook_registrations where company_id=? and connection_id=?",
        companyId, connectionId);
  }

  @Transactional
  public void disconnect(Long connectionId) {
    jdbc.update(
        "delete from zoho_mail_webhook_registrations where connection_id=?", connectionId);
  }

  @Transactional
  public boolean authenticateAndQueue(
      Long connectionId,
      String urlToken,
      String signature,
      String initialSecret,
      byte[] body) {
    if (!properties.isEnabled() || !properties.isWebhookEnabled()
        || connectionId == null || urlToken == null || urlToken.length() > 128
        || body == null || body.length == 0 || body.length > MAX_PAYLOAD_BYTES) {
      return false;
    }

    List<Registration> rows =
        jdbc.query(
            """
            select company_id, token_sha256, signing_secret_encrypted
            from zoho_mail_webhook_registrations
            where connection_id=? for update
            """,
            (rs, row) ->
                new Registration(
                    rs.getLong("company_id"), rs.getString("token_sha256"),
                    rs.getString("signing_secret_encrypted")),
            connectionId);
    if (rows.isEmpty()) return false;
    Registration row = rows.getFirst();
    if (!MessageDigest.isEqual(
        row.tokenHash().getBytes(StandardCharsets.UTF_8),
        sha256(urlToken).getBytes(StandardCharsets.UTF_8))) {
      return false;
    }
    ChannelConnection connection = connections.findByIdAndCompanyId(connectionId, row.companyId())
        .orElse(null);
    if (connection == null || connection.getProviderType() != ProviderType.ZOHO_MAIL
        || connection.getStatus() != ChannelConnectionStatus.ACTIVE) return false;

    String secret;
    String encryptedSecret = row.encryptedSecret();
    if (encryptedSecret != null && !encryptedSecret.isBlank()) {
      secret = cipher.decrypt(encryptedSecret);
    } else {
      if (initialSecret == null || initialSecret.length() < 16
          || initialSecret.length() > 1024) return false;
      secret = initialSecret;
    }
    if (!ZohoMailWebhookSignature.matches(secret, signature, body)) return false;

    try {
      JsonNode parsed = objectMapper.readTree(body);
      if (parsed == null || !parsed.isObject()) return false;
    } catch (Exception exception) {
      return false;
    }

    String encrypted =
        encryptedSecret == null || encryptedSecret.isBlank()
            ? cipher.encrypt(secret) : encryptedSecret;
    jdbc.update(
        """
        update zoho_mail_webhook_registrations
        set signing_secret_encrypted=?,
            status='ACTIVE',
            pending_at=clock_timestamp(),
            last_received_at=clock_timestamp(),
            next_attempt_at=now(),
            updated_at=now()
        where connection_id=? and company_id=?
        """,
        encrypted,
        connectionId,
        row.companyId());
    return true;
  }

  private String validatedWebhookBase() {
    String value = properties.getWebhookPublicBaseUrl();
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getRawQuery() != null || uri.getRawFragment() != null
          || (uri.getRawPath() != null && !uri.getRawPath().isBlank()
              && !"/".equals(uri.getRawPath()))) {
        throw new IllegalArgumentException("Invalid Zoho webhook base URL");
      }
      return uri.getScheme() + "://" + uri.getAuthority();
    } catch (RuntimeException exception) {
      throw new BadRequestException("ZOHO_MAIL_WEBHOOK_PUBLIC_BASE_URL must be an HTTPS origin");
    }
  }

  private void requireEnabled() {
    if (!properties.isEnabled() || !properties.isWebhookEnabled()) {
      throw new BadRequestException("Zoho Mail webhooks are not enabled");
    }
  }

  private String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  public record Setup(String webhookUrl, String status) {}
  public record Status(boolean configured, String status, Instant lastReceivedAt,
                       Instant lastProcessedAt) {}
  private record Registration(long companyId, String tokenHash, String encryptedSecret) {}
}
