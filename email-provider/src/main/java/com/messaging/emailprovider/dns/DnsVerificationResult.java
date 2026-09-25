package com.messaging.emailprovider.dns;

public record DnsVerificationResult(boolean ownershipVerified, boolean dkimVerified) {

  public boolean fullyVerified() {
    return ownershipVerified && dkimVerified;
  }
}
