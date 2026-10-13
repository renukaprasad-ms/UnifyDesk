package com.messaging.company.iam;

import com.messaging.company.enums.CompanyRole;
import java.util.Set;

public final class CompanyPermissionKeys {

  public static final String COMPANY_READ = "company.read";
  public static final String COMPANY_MODIFY = "company.modify";
  public static final String COMPANY_DELETE = "company.delete";
  public static final String COMPANY_TRANSFER_OWNERSHIP = "company.transfer_ownership";

  public static final String USER_READ = "user.read";
  public static final String USER_INVITE = "user.invite";
  public static final String USER_MODIFY = "user.modify";
  public static final String USER_REMOVE = "user.remove";

  public static final String ROLE_READ = "role.read";
  public static final String ROLE_CREATE = "role.create";
  public static final String ROLE_MODIFY = "role.modify";
  public static final String ROLE_DELETE = "role.delete";
  public static final String ROLE_ASSIGN = "role.assign";

  public static final String EMAIL_CONNECT = "email.connect";
  public static final String EMAIL_DISCONNECT = "email.disconnect";
  public static final String EMAIL_READ = "email.read";
  public static final String EMAIL_SEND = "email.send";
  public static final String EMAIL_MODIFY = "email.modify";

  public static final String WHATSAPP_CONNECT = "whatsapp.connect";
  public static final String WHATSAPP_DISCONNECT = "whatsapp.disconnect";
  public static final String WHATSAPP_READ = "whatsapp.read";
  public static final String WHATSAPP_SEND = "whatsapp.send";
  public static final String WHATSAPP_MODIFY = "whatsapp.modify";
  public static final String WHATSAPP_TEMPLATE_READ = "whatsapp.template.read";
  public static final String WHATSAPP_TEMPLATE_CREATE = "whatsapp.template.create";
  public static final String WHATSAPP_TEMPLATE_MODIFY = "whatsapp.template.modify";
  public static final String WHATSAPP_TEMPLATE_DELETE = "whatsapp.template.delete";

  public static final String SMS_CONNECT = "sms.connect";
  public static final String SMS_DISCONNECT = "sms.disconnect";
  public static final String SMS_READ = "sms.read";
  public static final String SMS_SEND = "sms.send";
  public static final String SMS_MODIFY = "sms.modify";
  public static final String SMS_SENDER_READ = "sms.sender.read";
  public static final String SMS_SENDER_CREATE = "sms.sender.create";
  public static final String SMS_SENDER_MODIFY = "sms.sender.modify";
  public static final String SMS_SENDER_DELETE = "sms.sender.delete";

  public static final String BROADCAST_READ = "broadcast.read";
  public static final String BROADCAST_CREATE = "broadcast.create";
  public static final String BROADCAST_SEND = "broadcast.send";
  public static final String BROADCAST_MODIFY = "broadcast.modify";
  public static final String BROADCAST_DELETE = "broadcast.delete";

  public static final String CONTACT_READ = "contact.read";
  public static final String CONTACT_CREATE = "contact.create";
  public static final String CONTACT_MODIFY = "contact.modify";
  public static final String CONTACT_DELETE = "contact.delete";
  public static final String CONTACT_IMPORT = "contact.import";
  public static final String CONTACT_EXPORT = "contact.export";
  public static final String CONTACT_CONSENT_READ = "contact.consent.read";
  public static final String CONTACT_CONSENT_MODIFY = "contact.consent.modify";

  public static final String CRM_LEAD_READ = "crm.lead.read";
  public static final String CRM_LEAD_MODIFY = "crm.lead.modify";
  public static final String CRM_ACCOUNT_READ = "crm.account.read";
  public static final String CRM_ACCOUNT_MODIFY = "crm.account.modify";
  public static final String CRM_TASK_READ = "crm.task.read";
  public static final String CRM_TASK_MODIFY = "crm.task.modify";
  public static final String CRM_TASK_WORKFLOW_MODIFY = "crm.task.workflow.modify";
  public static final String CRM_DEAL_READ = "crm.deal.read";
  public static final String CRM_DEAL_MODIFY = "crm.deal.modify";
  public static final String CRM_ACTIVITY_READ = "crm.activity.read";
  public static final String CRM_ACTIVITY_MODIFY = "crm.activity.modify";
  public static final String CRM_REPORT_READ = "crm.report.read";

  public static final String TEMPLATE_READ = "template.read";
  public static final String TEMPLATE_CREATE = "template.create";
  public static final String TEMPLATE_MODIFY = "template.modify";
  public static final String TEMPLATE_DELETE = "template.delete";

  public static final String BILLING_READ = "billing.read";
  public static final String BILLING_BALANCE_READ = "billing.balance.read";
  public static final String BILLING_BALANCE_ADD = "billing.balance.add";
  public static final String BILLING_CARD_READ = "billing.card.read";
  public static final String BILLING_CARD_ADD = "billing.card.add";
  public static final String BILLING_CARD_MODIFY = "billing.card.modify";
  public static final String BILLING_CARD_DELETE = "billing.card.delete";
  public static final String BILLING_INVOICE_READ = "billing.invoice.read";
  public static final String BILLING_INVOICE_DOWNLOAD = "billing.invoice.download";
  public static final String BILLING_SUBSCRIPTION_READ = "billing.subscription.read";
  public static final String BILLING_SUBSCRIPTION_MODIFY = "billing.subscription.modify";
  public static final String BILLING_SUBSCRIPTION_CANCEL = "billing.subscription.cancel";

  public static final String ADS_CONNECT = "ads.connect";
  public static final String ADS_DISCONNECT = "ads.disconnect";
  public static final String ADS_READ = "ads.read";
  public static final String ADS_CREATE = "ads.create";
  public static final String ADS_MODIFY = "ads.modify";
  public static final String ADS_DELETE = "ads.delete";
  public static final String ADS_BALANCE_ADD = "ads.balance.add";

  public static final String MEDIA_READ = "media.read";
  public static final String MEDIA_UPLOAD = "media.upload";
  public static final String MEDIA_DELETE = "media.delete";

  public static final String SETTINGS_READ = "settings.read";
  public static final String SETTINGS_MODIFY = "settings.modify";

  private static final Set<String> MANAGER_DEFAULTS =
      Set.of(
          COMPANY_READ,
          USER_READ,
          USER_INVITE,
          ROLE_READ,
          EMAIL_CONNECT,
          EMAIL_DISCONNECT,
          EMAIL_READ,
          EMAIL_SEND,
          EMAIL_MODIFY,
          WHATSAPP_CONNECT,
          WHATSAPP_DISCONNECT,
          WHATSAPP_READ,
          WHATSAPP_SEND,
          WHATSAPP_MODIFY,
          WHATSAPP_TEMPLATE_READ,
          WHATSAPP_TEMPLATE_CREATE,
          WHATSAPP_TEMPLATE_MODIFY,
          SMS_CONNECT,
          SMS_DISCONNECT,
          SMS_READ,
          SMS_SEND,
          SMS_MODIFY,
          SMS_SENDER_READ,
          SMS_SENDER_CREATE,
          SMS_SENDER_MODIFY,
          BROADCAST_READ,
          BROADCAST_CREATE,
          BROADCAST_SEND,
          BROADCAST_MODIFY,
          CONTACT_READ,
          CONTACT_CREATE,
          CONTACT_MODIFY,
          CONTACT_IMPORT,
          CONTACT_EXPORT,
          CONTACT_CONSENT_READ,
          CONTACT_CONSENT_MODIFY,
          CRM_LEAD_READ,
          CRM_LEAD_MODIFY,
          CRM_ACCOUNT_READ,
          CRM_ACCOUNT_MODIFY,
          CRM_TASK_READ,
          CRM_TASK_MODIFY,
          CRM_TASK_WORKFLOW_MODIFY,
          CRM_DEAL_READ,
          CRM_DEAL_MODIFY,
          CRM_ACTIVITY_READ,
          CRM_ACTIVITY_MODIFY,
          CRM_REPORT_READ,
          TEMPLATE_READ,
          TEMPLATE_CREATE,
          TEMPLATE_MODIFY,
          BILLING_READ,
          BILLING_INVOICE_READ,
          BILLING_SUBSCRIPTION_READ,
          ADS_CONNECT,
          ADS_DISCONNECT,
          ADS_READ,
          ADS_CREATE,
          ADS_MODIFY,
          MEDIA_READ,
          MEDIA_UPLOAD,
          SETTINGS_READ);

  private static final Set<String> MEMBER_DEFAULTS =
      Set.of(
          COMPANY_READ,
          EMAIL_READ,
          EMAIL_SEND,
          WHATSAPP_READ,
          WHATSAPP_SEND,
          SMS_SEND,
          BROADCAST_READ,
          CONTACT_READ,
          CONTACT_CONSENT_READ,
          CRM_LEAD_READ,
          CRM_ACCOUNT_READ,
          CRM_TASK_READ,
          TEMPLATE_READ,
          CRM_DEAL_READ,
          CRM_ACTIVITY_READ,
          CRM_REPORT_READ,
          ADS_READ,
          MEDIA_READ,
          MEDIA_UPLOAD);

  private CompanyPermissionKeys() {}

  public static Set<String> defaultsFor(CompanyRole role) {
    return switch (role) {
      case OWNER -> Set.of();
      case MANAGER -> MANAGER_DEFAULTS;
      case MEMBER -> MEMBER_DEFAULTS;
    };
  }
}
