package com.messaging.channel.meta.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.CoexistenceState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse.NextAction;
import java.time.Instant;

public record MetaWhatsAppConnectionHealthResponse(
    String connectionId,
    ChannelConnectionStatus genericStatus,
    ProductState status,
    AuthState authState,
    RegistrationState registrationState,
    SubscriptionState subscriptionState,
    CoexistenceState coexistenceState,
    Instant lastValidatedAt,
    MetaWhatsAppConnectionResponse.Phone phone,
    String safeErrorCode,
    NextAction nextAction) {}
