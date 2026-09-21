package com.messaging.channel.email;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.provider.ChannelProvider;

public interface EmailSendProvider extends ChannelProvider {

  EmailSendResult send(ChannelConnection connection, EmailSendCommand command);
}
