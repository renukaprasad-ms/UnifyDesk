package com.messaging.channel.email;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.provider.ChannelProvider;

public interface EmailSyncProvider extends ChannelProvider {

  EmailSyncQueuedResult queueSync(
      ChannelConnection connection, Long userId, EmailSyncRequest request);

  AsyncJobResponse activeSync(ChannelConnection connection);
}
