package com.messaging.admin.dto;
import com.messaging.billing.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
public record PlatformPlanRequest(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=120) String name,@NotNull BillingCycle billingCycle,@NotNull @DecimalMin("0.0") BigDecimal basePrice,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,@Min(1) int version,Map<String,String> entitlements) {}
