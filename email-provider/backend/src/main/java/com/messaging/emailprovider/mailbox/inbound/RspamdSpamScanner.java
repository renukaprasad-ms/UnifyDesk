package com.messaging.emailprovider.mailbox.inbound;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Rspamd /checkv2 on a loopback-only port. Unexpected responses fail closed. */
@Component
@ConditionalOnProperty(prefix = "app.mail.scanner", name = "enabled", havingValue = "true")
public class RspamdSpamScanner {
  private final URI url;
  private final JsonMapper json;

  public enum Decision { INBOX, SPAM, QUARANTINE }

  public RspamdSpamScanner(
      @Value("${app.mail.scanner.rspamd-url:http://127.0.0.1:11333/checkv2}") String target,
      JsonMapper json) {
    this.url = URI.create(target);
    if (!"http".equals(url.getScheme())
        || !"/checkv2".equals(url.getPath())
        || url.getQuery() != null || url.getUserInfo() != null
        || !("127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost()))) {
      throw new IllegalStateException("Rspamd must be on a private loopback-only listener");
    }
    this.json = json;
  }

  static Decision classify(JsonNode node) {
    if (node == null || !node.isObject()
        || node.path("is_skipped").asBoolean(false)) {
      throw new ServiceUnavailableException("Spam scan was skipped");
    }
    JsonNode actionNode = node.get("action");
    JsonNode score = node.get("score");
    JsonNode threshold = node.get("required_score");
    if (actionNode == null || !actionNode.isTextual()
        || score == null || !score.isNumber()
        || threshold == null || !threshold.isNumber()) {
      throw new ServiceUnavailableException("Incomplete spam scan result");
    }
    String action = actionNode.asText();
    if ("reject".equals(action) || "discard".equals(action)) return Decision.QUARANTINE;
    if ("add header".equals(action) || "rewrite subject".equals(action)
        || score.doubleValue() >= threshold.doubleValue()) return Decision.SPAM;
    if ("no action".equals(action) && score.doubleValue() < threshold.doubleValue()) {
      return Decision.INBOX;
  
  }

  public Decision scan(InputStream mime, long bytes) {
    if (bytes < 1 || bytes > 10485760L) {
      throw new ServiceUnavailableException("Invalid MIME length for spam scan");
    }
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) url.toURL().openConnection();
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(30000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "message/rfc822");
      connection.setFixedLengthStreamingMode(bytes);
      connection.setDoOutput(true);
      try (var out = connection.getOutputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        long seen = 0;
        while ((count = mime.read(buffer)) != -1) {
          seen += count;
          if (seen > bytes) throw new IOException("MIME length changed");
          out.write(buffer, 0, count);
        }
        if (seen != bytes) throw new IOException("MIME length mismatch");
      }
      if (connection.getResponseCode() != 200) {
        throw new ServiceUnavailableException("Spam scanner did not complete");
      }
      byte[] reply;
      try (InputStream stream = connection.getInputStream()) {
        reply = stream.readNBytes(65537);
      }
      if (reply.length > 65536) {
        throw new ServiceUnavailableException("Oversized spam scanner response");
      }
      return classify(json.readTree(reply));
    } catch (IOException | RuntimeException failure) {
      if (failure instanceof ServiceUnavailableException unavailable) throw unavailable;
      throw new ServiceUnavailableException("Spam scanner unavailable", failure);
    } finally {
      if (connection != null) connection.disconnect();
    }
  }
}
