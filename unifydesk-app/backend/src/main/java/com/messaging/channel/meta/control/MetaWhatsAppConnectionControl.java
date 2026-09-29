package com.messaging.channel.meta.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_whatsapp_connections")
public class MetaWhatsAppConnectionControl {

  @Id
  @Column(name = "connection_id")
  private Long connectionId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "account_model_version", nullable = false, length = 24)
  private String accountModelVersion = "LEGACY_WABA";

  @Column(name = "legacy_waba_id", length = 255)
  private String legacyWabaId;

  @Column(name = "whatsapp_account_id", length = 255)
  private String whatsappAccountId;

  @Column(name = "messaging_account_id", length = 255)
  private String messagingAccountId;

  @Column(name = "selected_phone_number_id", nullable = false, length = 255)
  private String selectedPhoneNumberId;

  @Enumerated(EnumType.STRING)
  @Column(name = "product_state", nullable = false, length = 32)
  private ProductState productState;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_state", nullable = false, length = 32)
  private AuthState authState;

  @Enumerated(EnumType.STRING)
  @Column(name = "registration_state", nullable = false, length = 32)
  private RegistrationState registrationState;

  @Enumerated(EnumType.STRING)
  @Column(name = "subscription_state", nullable = false, length = 32)
  private SubscriptionState subscriptionState;

  @Enumerated(EnumType.STRING)
  @Column(name = "coexistence_state", nullable = false, length = 32)
  private CoexistenceState coexistenceState;

  @Column(name = "coexistence_enabled", nullable = false)
  private boolean coexistenceEnabled;

  @Column(name = "last_validated_at")
  private Instant lastValidatedAt;

  @Column(name = "next_reconcile_at")
  private Instant nextReconcileAt;

  @Column(name = "last_provider_http_status")
  private Integer lastProviderHttpStatus;

  @Column(name = "last_provider_error_type", length = 120)
  private String lastProviderErrorType;

  @Column(name = "last_provider_error_code")
  private Integer lastProviderErrorCode;

  @Column(name = "last_provider_error_subcode")
  private Integer lastProviderErrorSubcode;

  @Column(name = "last_provider_fbtrace_id", length = 255)
  private String lastProviderFbtraceId;

  @Column(name = "last_safe_error_code", length = 80)
  private String lastSafeErrorCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    Instant now = Instant.now();
    if (productState == null) {
      productState = ProductState.CONNECTING;
    }
    if (authState == null) {
      authState = AuthState.UNKNOWN;
    }
    if (registrationState == null) {
      registrationState = RegistrationState.PENDING;
    }
    if (subscriptionState == null) {
      subscriptionState = SubscriptionState.PENDING;
    }
    if (coexistenceState == null) {
      coexistenceState = CoexistenceState.DISABLED;
    }
    if (accountModelVersion == null || accountModelVersion.isBlank()) {
      accountModelVersion = "LEGACY_WABA";
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }

  public enum ProductState {
    CONNECTING,
    VALIDATING,
    ACTIVE,
    DEGRADED,
    ACTION_REQUIRED,
    REAUTH_REQUIRED,
    DISCONNECTED,
    ERROR
  }

  public enum AuthState {
    VALID,
    EXPIRING,
    EXPIRED,
    REVOKED,
    MISSING_PERMISSION,
    UNKNOWN
  }

  public enum RegistrationState {
    PENDING,
    REGISTERED,
    ACTION_REQUIRED,
    ERROR
  }

  public enum SubscriptionState {
    PENDING,
    SUBSCRIBED,
    ERROR
  }

  public enum CoexistenceState {
    DISABLED,
    ELIGIBLE_UNVERIFIED,
    ENABLED,
    ERROR
  }
}
