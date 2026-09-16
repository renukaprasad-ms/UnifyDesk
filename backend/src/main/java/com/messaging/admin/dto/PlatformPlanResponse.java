package com.messaging.admin.dto;
import com.messaging.billing.enums.*;
import java.math.BigDecimal;
import java.util.Map;
public record PlatformPlanResponse(String id,String code,String name,PlanStatus status,BillingCycle billingCycle,BigDecimal basePrice,String currency,int version,Map<String,String> entitlements) {}
