package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.ChannelSyncState;
import com.messaging.inbox.repository.ChannelSyncStateRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImapSmtpSyncStateService {

  private final ChannelSyncStateRepository repository;
  private final ImapSmtpCursorCodec cursorCodec;

  @Transactional(readOnly = true)
  public ImapSmtpCursorState read(Long connectionId) {
    return repository
        .findByConnectionIdAndProviderType(connectionId, ProviderType.IMAP_SMTP)
        .map(ChannelSyncState::getCursorValue)
        .map(cursorCodec::decode)
        .orElseGet(ImapSmtpCursorState::empty);
  }

  @Transactional
  public void advance(
      ChannelConnection connection,
      EmailFolder folder,
      long uidValidity,
      long lastUid) {
    ChannelSyncState state =
        repository
            .findForUpdate(connection.getId(), ProviderType.IMAP_SMTP)
            .orElseGet(
                () -> {
                  ChannelSyncState created = new ChannelSyncState();
                  created.setConnection(connection);
                  created.setProviderType(ProviderType.IMAP_SMTP);
                  return created;
                });

    ImapSmtpCursorState current = cursorCodec.decode(state.getCursorValue());
    ImapSmtpCursorState updated =
        current.with(
            folder,
            new ImapSmtpCursorState.FolderCursor(
                Math.max(0, uidValidity), Math.max(0, lastUid)));
    state.setCursorValue(cursorCodec.encode(updated));
    state.setLastIncrementalSyncAt(Instant.now());
    repository.save(state);
  }
}
