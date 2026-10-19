package com.messaging.billing.service;

import com.messaging.billing.entity.CompanyBillingProfile;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.repository.CompanyBillingProfileRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.service.NotificationService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingNotificationService {

  private final NotificationService notificationService;
  private final NotificationProperties notificationProperties;
  private final CompanyBillingProfileRepository billingProfileRepository;
  private final CompanyRepository companyRepository;
  private final InvoiceRepository invoiceRepository;
  private final CompanySubscriptionRepository subscriptionRepository;

  private static final NumberFormat INR_FORMAT =
      NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

  public void sendRenewalInvoiceEmail(Long companyId, Long invoiceId) {
    try {
      String recipient = resolveBillingEmail(companyId);
      if (recipient == null || recipient.isBlank()) {
        log.warn("Cannot send renewal invoice email: no billing email for company {}", companyId);
        return;
      }

      Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
      if (invoice == null) {
        log.warn("Cannot send renewal invoice email: invoice {} not found", invoiceId);
        return;
      }

      String companyName = resolveCompanyName(companyId);
      String invoiceNum =
          invoice.getInvoiceNumber() != null
              ? invoice.getInvoiceNumber()
              : String.valueOf(invoiceId);
      String formattedAmount = formatCurrency(invoice.getTotal());

      String subject = String.format("Invoice Paid: Subscription Renewal (%s)", invoiceNum);
      String html =
          String.format(
              """
              <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1e293b;">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h1 style="font-size: 24px; font-weight: 800; color: #0f172a; margin: 0;">UnifyDesk</h1>
                  <p style="color: #64748b; font-size: 14px; margin-top: 4px;">Subscription Renewal Confirmation</p>
                </div>
                <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 24px; margin-bottom: 24px;">
                  <p style="margin: 0 0 12px 0; font-size: 16px;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px;">Your subscription has been renewed successfully via AutoPay. Here are your transaction details:</p>
                  <table style="width: 100%%; border-collapse: collapse; font-size: 14px;">
                    <tr>
                      <td style="padding: 8px 0; color: #64748b;">Invoice Number:</td>
                      <td style="padding: 8px 0; text-align: right; font-weight: 600;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px 0; color: #64748b;">Amount Paid:</td>
                      <td style="padding: 8px 0; text-align: right; font-weight: 700; color: #059669;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px 0; color: #64748b;">Status:</td>
                      <td style="padding: 8px 0; text-align: right; font-weight: 600; color: #059669;">PAID</td>
                    </tr>
                  </table>
                </div>
                <div style="text-align: center; margin-top: 24px;">
                  <a href="%s/company/plan" style="background: #4f46e5; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; display: inline-block;">View in Billing Dashboard</a>
                </div>
                <p style="color: #94a3b8; font-size: 12px; text-align: center; margin-top: 32px;">If you have any questions, contact billing support at %s.</p>
              </div>
              """,
              companyName,
              invoiceNum,
              formattedAmount,
              notificationProperties.getEmail().getWebsiteUrl(),
              notificationProperties.getEmail().getBillingFrom());

      notificationService.send(
          SendNotificationRequest.billingEmail(recipient, subject, html, true));
      log.info("Sent renewal invoice email to {} for invoice {}", recipient, invoiceId);
    } catch (Exception e) {
      log.error(
          "Failed to send renewal invoice email for company {}: {}", companyId, e.getMessage());
    }
  }

  public void sendRenewalFailureEmail(Long companyId, Long subscriptionId, int dayCount) {
    try {
      String recipient = resolveBillingEmail(companyId);
      if (recipient == null || recipient.isBlank()) return;

      CompanySubscription sub = subscriptionRepository.findById(subscriptionId).orElse(null);
      String planName =
          sub != null && sub.getPlan() != null ? sub.getPlan().getName() : "Subscription Plan";
      String companyName = resolveCompanyName(companyId);

      String subject =
          String.format("[Action Required] AutoPay Renewal Failed (Day %d of 2)", dayCount);
      String html =
          String.format(
              """
              <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1e293b;">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h1 style="font-size: 24px; font-weight: 800; color: #dc2626; margin: 0;">Payment Failed</h1>
                  <p style="color: #64748b; font-size: 14px; margin-top: 4px;">UnifyDesk AutoPay Renewal Notice</p>
                </div>
                <div style="background: #fef2f2; border: 1px solid #fecaca; border-radius: 12px; padding: 24px; margin-bottom: 24px;">
                  <p style="margin: 0 0 12px 0; font-size: 16px;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px; color: #991b1b;">
                    We were unable to process the automatic renewal payment for your <strong>%s</strong>.
                  </p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px;">
                    We will retry automatically for <strong>2 days</strong>. If the payment is not completed by Day 3, your workspace services will be <strong>suspended/frozen</strong>.
                  </p>
                </div>
                <div style="text-align: center; margin-top: 24px;">
                  <a href="%s/company/plan" style="background: #dc2626; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; display: inline-block;">Update AutoPay / Pay Now</a>
                </div>
              </div>
              """,
              companyName, planName, notificationProperties.getEmail().getWebsiteUrl());

      notificationService.send(
          SendNotificationRequest.billingEmail(recipient, subject, html, true));
      log.info("Sent renewal failure notice (day {}) to company {}", dayCount, companyId);
    } catch (Exception e) {
      log.error(
          "Failed to send renewal failure email for company {}: {}", companyId, e.getMessage());
    }
  }

  public void sendCompanyFrozenEmail(Long companyId, Long subscriptionId) {
    try {
      String recipient = resolveBillingEmail(companyId);
      if (recipient == null || recipient.isBlank()) return;

      CompanySubscription sub = subscriptionRepository.findById(subscriptionId).orElse(null);
      String planName =
          sub != null && sub.getPlan() != null ? sub.getPlan().getName() : "Subscription Plan";
      String companyName = resolveCompanyName(companyId);

      String subject = "[URGENT] Workspace Suspended Due to Unpaid Subscription Renewal";
      String html =
          String.format(
              """
              <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1e293b;">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h1 style="font-size: 24px; font-weight: 800; color: #b91c1c; margin: 0;">Workspace Suspended</h1>
                  <p style="color: #64748b; font-size: 14px; margin-top: 4px;">UnifyDesk Account Restriction</p>
                </div>
                <div style="background: #fef2f2; border: 1px solid #f87171; border-radius: 12px; padding: 24px; margin-bottom: 24px;">
                  <p style="margin: 0 0 12px 0; font-size: 16px;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px; color: #7f1d1d;">
                    Following consecutive failed renewal attempts, your workspace has been <strong>frozen and suspended</strong>. Outgoing messaging broadcasts and automations are currently paused.
                  </p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px;">
                    To instantly restore your workspace and resume services, please make the renewal payment or update your payment method.
                  </p>
                </div>
                <div style="text-align: center; margin-top: 24px;">
                  <a href="%s/company/plan" style="background: #b91c1c; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; display: inline-block;">Reactivate Workspace</a>
                </div>
              </div>
              """,
              companyName, notificationProperties.getEmail().getWebsiteUrl());

      notificationService.send(
          SendNotificationRequest.billingEmail(recipient, subject, html, true));
      log.info("Sent workspace suspension notice to company {}", companyId);
    } catch (Exception e) {
      log.error("Failed to send suspension email for company {}: {}", companyId, e.getMessage());
    }
  }

  public void sendWalletTopupSuccessEmail(
      Long companyId, BigDecimal amount, BigDecimal newBalance) {
    try {
      String recipient = resolveBillingEmail(companyId);
      if (recipient == null || recipient.isBlank()) return;

      String companyName = resolveCompanyName(companyId);
      String subject = String.format("AutoPay: Wallet Recharged with %s", formatCurrency(amount));
      String html =
          String.format(
              """
              <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1e293b;">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h1 style="font-size: 24px; font-weight: 800; color: #0f172a; margin: 0;">Wallet Recharged</h1>
                  <p style="color: #64748b; font-size: 14px; margin-top: 4px;">UnifyDesk Prepaid Wallet</p>
                </div>
                <div style="background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 12px; padding: 24px; margin-bottom: 24px;">
                  <p style="margin: 0 0 12px 0; font-size: 16px;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px;">
                    Your prepaid wallet was automatically recharged via your saved AutoPay payment method.
                  </p>
                  <table style="width: 100%%; border-collapse: collapse; font-size: 14px;">
                    <tr>
                      <td style="padding: 8px 0; color: #64748b;">Top-Up Amount:</td>
                      <td style="padding: 8px 0; text-align: right; font-weight: 700; color: #15803d;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px 0; color: #64748b;">New Wallet Balance:</td>
                      <td style="padding: 8px 0; text-align: right; font-weight: 700; color: #0f172a;">%s</td>
                    </tr>
                  </table>
                </div>
                <div style="text-align: center; margin-top: 24px;">
                  <a href="%s/company/billing" style="background: #15803d; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; display: inline-block;">View Wallet &amp; Transactions</a>
                </div>
              </div>
              """,
              companyName,
              formatCurrency(amount),
              formatCurrency(newBalance),
              notificationProperties.getEmail().getWebsiteUrl());

      notificationService.send(
          SendNotificationRequest.billingEmail(recipient, subject, html, true));
      log.info("Sent wallet auto-recharge success email to company {}", companyId);
    } catch (Exception e) {
      log.error(
          "Failed to send wallet topup success email for company {}: {}",
          companyId,
          e.getMessage());
    }
  }

  public void sendWalletTopupFailureEmail(
      Long companyId, BigDecimal amount, int attemptCount, boolean paused) {
    try {
      String recipient = resolveBillingEmail(companyId);
      if (recipient == null || recipient.isBlank()) return;

      String companyName = resolveCompanyName(companyId);
      String subject =
          paused
              ? "[Action Required] Wallet Auto-Recharge Paused after 3 Failed Attempts"
              : String.format(
                  "[Alert] Wallet Auto-Recharge Failed (Attempt %d of 3)", attemptCount);

      String alertMessage =
          paused
              ? "We tried to automatically reload your wallet 3 consecutive times, but all attempts failed. To protect your account, <strong>Auto-Recharge has been paused</strong>. Please update your payment method to ensure uninterrupted messaging."
              : String.format(
                  "An automatic wallet reload of %s failed (attempt %d of 3). We will retry automatically. Please ensure sufficient funds or a valid mandate on your linked payment method.",
                  formatCurrency(amount), attemptCount);

      String html =
          String.format(
              """
              <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1e293b;">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h1 style="font-size: 24px; font-weight: 800; color: #ea580c; margin: 0;">Auto-Recharge Alert</h1>
                  <p style="color: #64748b; font-size: 14px; margin-top: 4px;">UnifyDesk Prepaid Wallet</p>
                </div>
                <div style="background: #fff7ed; border: 1px solid #fed7aa; border-radius: 12px; padding: 24px; margin-bottom: 24px;">
                  <p style="margin: 0 0 12px 0; font-size: 16px;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 16px 0; line-height: 1.5; font-size: 14px; color: #9a3412;">
                    %s
                  </p>
                </div>
                <div style="text-align: center; margin-top: 24px;">
                  <a href="%s/company/billing" style="background: #ea580c; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; display: inline-block;">Manage Wallet &amp; AutoPay</a>
                </div>
              </div>
              """,
              companyName, alertMessage, notificationProperties.getEmail().getWebsiteUrl());

      notificationService.send(
          SendNotificationRequest.billingEmail(recipient, subject, html, true));
      log.info(
          "Sent wallet auto-recharge failure notice (attempt {}, paused={}) to company {}",
          attemptCount,
          paused,
          companyId);
    } catch (Exception e) {
      log.error(
          "Failed to send wallet topup failure email for company {}: {}",
          companyId,
          e.getMessage());
    }
  }

  private String resolveBillingEmail(Long companyId) {
    return billingProfileRepository
        .findById(companyId)
        .map(CompanyBillingProfile::getBillingEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElseGet(
            () ->
                companyRepository.findById(companyId).map(Company::getBusinessEmail).orElse(null));
  }

  private String resolveCompanyName(Long companyId) {
    return companyRepository.findById(companyId).map(Company::getName).orElse("Valued Customer");
  }

  private String formatCurrency(BigDecimal amount) {
    if (amount == null) return "₹0.00";
    try {
      return INR_FORMAT.format(amount);
    } catch (Exception ignored) {
      return "₹" + amount.toPlainString();
    }
  }
}
