package com.messaging.inbox.dto;

import com.messaging.inbox.enums.InboxParticipantType;

public record InboxParticipantResponse(
    String address, String displayName, InboxParticipantType participantType) {}
