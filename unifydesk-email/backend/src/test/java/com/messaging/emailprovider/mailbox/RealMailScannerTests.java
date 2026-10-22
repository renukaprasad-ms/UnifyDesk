package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.*;

import com.messaging.emailprovider.mailbox.inbound.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "MAIL_QA_REAL_SCANNERS", matches = "YES")
class RealMailScannerTests {
  @Test
  void actualClamdRejectsLocalSignatureAndRspamdReturnsUsableVerdict() throws Exception {
    var clam = new ClamdMalwareScanner("127.0.0.1", 3310);
    var rspamd =
        new RspamdSpamScanner("http://127.0.0.1:11333/checkv2", JsonMapper.builder().build());
    byte[] clean =
        ("From: sender@example.invalid\r\nTo: owner@example.test\r\nSubject: QA\r\n"
                + "MIME-Version: 1.0\r\nContent-Type: text/plain\r\n\r\nSynthetic clean body\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    assertEquals(
        ClamdMalwareScanner.Verdict.CLEAN,
        clam.scan(new ByteArrayInputStream(clean), clean.length, digest(clean)));
    assertNotNull(rspamd.scan(new ByteArrayInputStream(clean), clean.length, digest(clean)));
    byte[] infected = "UnifyDesk QA malware fixture".getBytes(StandardCharsets.US_ASCII);
    assertEquals(
        ClamdMalwareScanner.Verdict.INFECTED,
        clam.scan(new ByteArrayInputStream(infected), infected.length, digest(infected)));
  }

  private static String digest(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
