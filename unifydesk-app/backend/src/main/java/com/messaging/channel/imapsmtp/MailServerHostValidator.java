package com.messaging.channel.imapsmtp;

import com.messaging.common.exception.BadRequestException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class MailServerHostValidator {

  public String validate(String host) {
    if (host == null || host.isBlank()) {
      throw new BadRequestException("Mail server host is required");
    }

    String normalized = IDN.toASCII(host.trim().toLowerCase());
    if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
      throw new BadRequestException("Private mail server addresses are not allowed");
    }

    try {
      InetAddress[] addresses = InetAddress.getAllByName(normalized);
      if (addresses.length == 0) {
        throw new BadRequestException("Mail server host could not be resolved");
      }
      for (InetAddress address : addresses) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()
            || isCarrierGradeNat(address)
            || isMetadataAddress(address)) {
          throw new BadRequestException("Private mail server addresses are not allowed");
        }
      }
      return normalized;
    } catch (UnknownHostException exception) {
      throw new BadRequestException("Mail server host could not be resolved");
    }
  }

  private boolean isCarrierGradeNat(InetAddress address) {
    byte[] bytes = address.getAddress();
    if (bytes.length != 4) return false;
    int first = Byte.toUnsignedInt(bytes[0]);
    int second = Byte.toUnsignedInt(bytes[1]);
    return first == 100 && second >= 64 && second <= 127;
  }

  private boolean isMetadataAddress(InetAddress address) {
    byte[] bytes = address.getAddress();
    if (bytes.length != 4) return false;
    return Byte.toUnsignedInt(bytes[0]) == 169 && Byte.toUnsignedInt(bytes[1]) == 254;
  }
}
