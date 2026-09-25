package com.messaging.channel.email;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.provider.ChannelProvider;
import java.util.List;

/** Email provider capability for synchronizing read/unread state with the remote mailbox. */
public interface EmailReadStateProvider extends ChannelProvider {

  void updateReadState(
      ChannelConnection connection,
      List<EmailReadStateMessage> messages,
      boolean read,
      boolean background);
}
