package com.messaging.emailprovider.validation.smtp;

import java.util.List;

public interface SmtpProbeClient {
  SmtpProbeResult probe(List<String> mxHosts, String recipient);
}
