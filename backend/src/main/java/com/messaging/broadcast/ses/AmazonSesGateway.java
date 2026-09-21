package com.messaging.broadcast.ses;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.common.exception.BadRequestException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Service
@RequiredArgsConstructor
public class AmazonSesGateway {

  private final AmazonSesClientManager clientManager;

  public AmazonSesAccountSnapshot inspect(
      BroadcastSenderConnection connection, String fromAddress) {
    SesV2Client client = clientManager.client(connection);
    try {
      GetAccountResponse account = client.getAccount(GetAccountRequest.builder().build());
      String identity = verifiedIdentity(client, fromAddress);
      SendQuota quota = account.sendQuota();
      return new AmazonSesAccountSnapshot(
          Boolean.TRUE.equals(account.productionAccessEnabled()),
          Boolean.TRUE.equals(account.sendingEnabled()),
          quota == null ? null : quota.max24HourSend(),
          quota == null ? null : quota.maxSendRate(),
          quota == null ? null : quota.sentLast24Hours(),
          identity);
    } catch (SesV2Exception exception) {
      throw new BadRequestException(
          "Unable to validate Amazon SES connection: " + awsMessage(exception));
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Unable to validate Amazon SES connection: " + safeMessage(exception));
    }
  }

  private String verifiedIdentity(SesV2Client client, String fromAddress) {
    String normalized = fromAddress.trim().toLowerCase(Locale.ROOT);
    if (isVerified(client, normalized)) return normalized;
    int at = normalized.lastIndexOf('@');
    if (at > 0 && at + 1 < normalized.length()) {
      String domain = normalized.substring(at + 1);
      if (isVerified(client, domain)) return domain;
    }
    throw new BadRequestException(
        "Amazon SES sender is not verified. Verify the email address or its domain in SES first.");
  }

  private boolean isVerified(SesV2Client client, String identity) {
    try {
      GetEmailIdentityResponse response =
          client.getEmailIdentity(
              GetEmailIdentityRequest.builder().emailIdentity(identity).build());
      return response.verificationStatus() == VerificationStatus.SUCCESS;
    } catch (software.amazon.awssdk.services.sesv2.model.NotFoundException exception) {
      return false;
    }
  }

  private String awsMessage(SesV2Exception exception) {
    if (exception.awsErrorDetails() != null
        && exception.awsErrorDetails().errorMessage() != null) {
      return exception.awsErrorDetails().errorMessage();
    }
    return safeMessage(exception);
  }

  private String safeMessage(Throwable throwable) {
    String value = throwable.getMessage();
    return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;
  }
}
