package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailAttachment;
import com.messaging.channel.email.EmailAttachmentDataSource;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendRequest;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SentDateTerm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImapSmtpMailClient {

  private static final int MAX_BODY_CHARS = 2_000_000;
  private static final List<String> SENT_FOLDER_CANDIDATES =
      List.of("Sent", "Sent Items", "Sent Mail", "INBOX.Sent", "INBOX/Sent");

  private final ImapSmtpProperties properties;
  private final MailServerHostValidator hostValidator;

  public TestedMailbox test(ImapSmtpStoredCredentials credentials) {
    String imapHost = hostValidator.validate(credentials.imapHost());
    String smtpHost = hostValidator.validate(credentials.smtpHost());
    ImapSmtpStoredCredentials safe =
        new ImapSmtpStoredCredentials(
            credentials.email(),
            credentials.displayName(),
            credentials.username(),
            credentials.password(),
            imapHost,
            credentials.imapPort(),
            credentials.imapSecurity(),
            smtpHost,
            credentials.smtpPort(),
            credentials.smtpSecurity(),
            credentials.sentFolder());

    Store store = null;
    try {
      store = openStore(safe);
      Folder inbox = store.getFolder("INBOX");
      if (inbox == null || !inbox.exists()) {
        throw new BadRequestException("IMAP server does not expose an INBOX folder");
      }
      String sentFolder = resolveSentFolder(store, safe.sentFolder());
      testSmtp(safe);
      return new TestedMailbox(sentFolder);
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to connect to the IMAP server with these credentials");
    } finally {
      closeQuietly(store);
    }
  }

  public FolderCursor currentCursor(
      ImapSmtpStoredCredentials credentials, EmailFolder emailFolder) {
    String folderName = folderName(credentials, emailFolder);
    Store store = null;
    try {
      store = openStore(credentials);
      Folder folder = store.getFolder(folderName);
      ensureFolder(folder, folderName);
      folder.open(Folder.READ_ONLY);
      try {
        if (!(folder instanceof UIDFolder uidFolder)) {
          throw new BadRequestException("IMAP server does not support UID-based synchronization");
        }
        long uidValidity = uidFolder.getUIDValidity();
        int count = folder.getMessageCount();
        long lastUid = 0;
        if (count > 0) {
          Message last = folder.getMessage(count);
          lastUid = Math.max(0, uidFolder.getUID(last));
        }
        return new FolderCursor(uidValidity, lastUid);
      } finally {
        folder.close(false);
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ServiceUnavailableException("Unable to read IMAP folder state", exception);
    } finally {
      closeQuietly(store);
    }
  }

  public List<MailboxMessage> history(
      ImapSmtpStoredCredentials credentials, EmailSyncRequest request, int maxMessages) {
    List<MailboxMessage> collected = new ArrayList<>();
    syncHistory(
        credentials,
        request,
        maxMessages,
        Math.min(50, Math.max(1, maxMessages)),
        (batch, total) -> collected.addAll(batch));
    return collected;
  }

  public int syncHistory(
      ImapSmtpStoredCredentials credentials,
      EmailSyncRequest request,
      int maxMessages,
      int batchSize,
      HistoryBatchConsumer consumer) {
    String folderName = folderName(credentials, request.folder());
    Store store = null;
    try {
      store = openStore(credentials);
      Folder folder = store.getFolder(folderName);
      ensureFolder(folder, folderName);
      folder.open(Folder.READ_ONLY);
      try {
        Message[] messages =
            request.mode() == EmailSyncMode.DATE_RANGE
                ? dateRangeMessages(folder, request.folder(), request.fromDate(), request.toDate())
                : latestMessages(folder, request.messageCount());

        int limit = Math.min(Math.max(1, maxMessages), messages.length);
        int start = Math.max(0, messages.length - limit);
        Message[] selected = java.util.Arrays.copyOfRange(messages, start, messages.length);
        int resolvedBatchSize = Math.max(1, Math.min(batchSize, 100));

        for (int offset = 0; offset < selected.length; offset += resolvedBatchSize) {
          int end = Math.min(selected.length, offset + resolvedBatchSize);
          Message[] batch = java.util.Arrays.copyOfRange(selected, offset, end);
          consumer.accept(snapshots(folder, batch, request.folder()), selected.length);
        }
        return selected.length;
      } finally {
        folder.close(false);
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (ServiceUnavailableException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ServiceUnavailableException(
          "Unable to read messages from the IMAP server", exception);
    } finally {
      closeQuietly(store);
    }
  }

  public FolderDelta incremental(
      ImapSmtpStoredCredentials credentials,
      EmailFolder emailFolder,
      long previousUidValidity,
      long previousUid,
      int maxMessages) {
    String folderName = folderName(credentials, emailFolder);
    Store store = null;
    try {
      store = openStore(credentials);
      Folder folder = store.getFolder(folderName);
      ensureFolder(folder, folderName);
      folder.open(Folder.READ_ONLY);
      try {
        if (!(folder instanceof UIDFolder uidFolder)) {
          throw new BadRequestException("IMAP server does not support UID-based synchronization");
        }

        long uidValidity = uidFolder.getUIDValidity();
        boolean uidValidityChanged = previousUidValidity > 0 && previousUidValidity != uidValidity;
        Message[] messages;

        int limit = Math.max(1, maxMessages);
        if (uidValidityChanged) {
          int messageCount = folder.getMessageCount();
          if (messageCount <= 0) {
            messages = new Message[0];
          } else {
            int start = Math.max(1, messageCount - limit + 1);
            messages = folder.getMessages(start, messageCount);
          }
        } else {
          long startUid = Math.max(1, previousUid + 1);
          messages = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID);
          if (messages == null) messages = new Message[0];
          if (messages.length > limit) {
            messages = java.util.Arrays.copyOfRange(messages, 0, limit);
          }
        }

        List<MailboxMessage> snapshots = snapshots(folder, messages, emailFolder);
        long lastUid = previousUidValidity == uidValidity ? previousUid : 0;
        for (MailboxMessage snapshot : snapshots) {
          lastUid = Math.max(lastUid, snapshot.uid());
        }

        return new FolderDelta(uidValidity, lastUid, snapshots);
      } finally {
        folder.close(false);
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ServiceUnavailableException(
          "Unable to incrementally sync the IMAP mailbox", exception);
    } finally {
      closeQuietly(store);
    }
  }

  public void updateReadState(
      ImapSmtpStoredCredentials credentials,
      EmailFolder emailFolder,
      List<String> externalMessageIds,
      boolean read) {
    if (externalMessageIds == null || externalMessageIds.isEmpty()) return;

    String folderName = folderName(credentials, emailFolder);
    Store store = null;
    try {
      store = openStore(credentials);
      Folder folder = store.getFolder(folderName);
      ensureFolder(folder, folderName);
      folder.open(Folder.READ_WRITE);
      try {
        if (!(folder instanceof UIDFolder uidFolder)) {
          throw new BadRequestException("IMAP server does not support UID-based synchronization");
        }

        long currentUidValidity = uidFolder.getUIDValidity();
        List<Long> uids = new ArrayList<>();
        for (String externalMessageId : externalMessageIds) {
          ImapMessageRef reference = parseMessageRef(externalMessageId);
          if (!emailFolder.providerKey().equalsIgnoreCase(reference.folderKey())) continue;
          if (reference.uidValidity() != currentUidValidity) {
            throw new BadRequestException(
                "IMAP folder identity changed. Sync the mailbox before changing read state");
          }
          uids.add(reference.uid());
        }

        if (uids.isEmpty()) return;
        Message[] messages =
            uidFolder.getMessagesByUID(uids.stream().mapToLong(Long::longValue).toArray());
        List<Message> existing =
            messages == null
                ? List.of()
                : java.util.Arrays.stream(messages).filter(java.util.Objects::nonNull).toList();
        if (!existing.isEmpty()) {
          folder.setFlags(existing.toArray(Message[]::new), new Flags(Flags.Flag.SEEN), read);
        }
      } finally {
        folder.close(false);
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ServiceUnavailableException("Unable to update IMAP message read state", exception);
    } finally {
      closeQuietly(store);
    }
  }

  public SentMessage send(ImapSmtpStoredCredentials credentials, ImapSmtpSendRequest request) {
    return send(credentials, request, null, null, List.of());
  }

  public SentMessage send(
      ImapSmtpStoredCredentials credentials,
      ImapSmtpSendRequest request,
      List<EmailAttachment> attachments) {
    return send(credentials, request, null, null, attachments);
  }

  public SentMessage send(
      ImapSmtpStoredCredentials credentials,
      ImapSmtpSendRequest request,
      String requestedMessageId,
      String broadcastCorrelationId) {
    return send(credentials, request, requestedMessageId, broadcastCorrelationId, List.of());
  }

  public SentMessage send(
      ImapSmtpStoredCredentials credentials,
      ImapSmtpSendRequest request,
      String requestedMessageId,
      String broadcastCorrelationId,
      List<EmailAttachment> attachments) {
    List<String> to = normalizeRecipients(request.to());
    List<String> cc = normalizeRecipients(request.cc());
    List<String> bcc = normalizeRecipients(request.bcc());
    if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    try {
      Session session = Session.getInstance(smtpProperties(credentials));
      MimeMessage message = new MimeMessage(session);
      InternetAddress from =
          credentials.displayName() == null || credentials.displayName().isBlank()
              ? new InternetAddress(credentials.email(), true)
              : new InternetAddress(
                  credentials.email(), credentials.displayName(), StandardCharsets.UTF_8.name());
      message.setFrom(from);
      setRecipients(message, Message.RecipientType.TO, to);
      setRecipients(message, Message.RecipientType.CC, cc);
      setRecipients(message, Message.RecipientType.BCC, bcc);
      message.setSubject(
          request.subject() == null ? "" : request.subject().trim(), StandardCharsets.UTF_8.name());
      if (attachments == null || attachments.isEmpty()) {
        message.setText(request.bodyText(), StandardCharsets.UTF_8.name());
      } else {
        MimeMultipart multipart = new MimeMultipart("mixed");
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(request.bodyText(), StandardCharsets.UTF_8.name());
        multipart.addBodyPart(bodyPart);
        for (EmailAttachment attachment : attachments) {
          MimeBodyPart attachmentPart = new MimeBodyPart();
          attachmentPart.setDataHandler(new DataHandler(new EmailAttachmentDataSource(attachment)));
          attachmentPart.setFileName(attachment.fileName());
          attachmentPart.setDisposition(Part.ATTACHMENT);
          multipart.addBodyPart(attachmentPart);
        }
        message.setContent(multipart);
      }

      if (hasText(request.inReplyTo())) {
        message.setHeader("In-Reply-To", request.inReplyTo().trim());
      }
      if (hasText(request.references())) {
        message.setHeader("References", request.references().trim());
      }

      Date sentAt = new Date();
      message.setSentDate(sentAt);
      message.saveChanges();
      if (hasText(requestedMessageId)) {
        message.setHeader("Message-ID", requestedMessageId.trim());
      }
      if (hasText(broadcastCorrelationId)) {
        message.setHeader("X-Messaging-Broadcast-Recipient", broadcastCorrelationId.trim());
      }

      Transport transport = null;
      try {
        transport = session.getTransport(smtpProtocol(credentials.smtpSecurity()));
        transport.connect(
            hostValidator.validate(credentials.smtpHost()),
            credentials.smtpPort(),
            credentials.username(),
            credentials.password());
        transport.sendMessage(message, message.getAllRecipients());
      } finally {
        closeQuietly(transport);
      }

      return new SentMessage(
          trimToNull(message.getMessageID()),
          to,
          cc,
          bcc,
          request.subject() == null ? "" : request.subject().trim(),
          sentAt.toInstant());
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ServiceUnavailableException(
          "Unable to send email through the SMTP server", exception);
    }
  }

  private Store openStore(ImapSmtpStoredCredentials credentials) throws Exception {
    String protocol = imapProtocol(credentials.imapSecurity());
    Session session = Session.getInstance(imapProperties(credentials));
    Store store = session.getStore(protocol);
    store.connect(
        hostValidator.validate(credentials.imapHost()),
        credentials.imapPort(),
        credentials.username(),
        credentials.password());
    return store;
  }

  private void testSmtp(ImapSmtpStoredCredentials credentials) {
    try {
      Session session = Session.getInstance(smtpProperties(credentials));
      Transport transport = null;
      try {
        transport = session.getTransport(smtpProtocol(credentials.smtpSecurity()));
        transport.connect(
            hostValidator.validate(credentials.smtpHost()),
            credentials.smtpPort(),
            credentials.username(),
            credentials.password());
      } finally {
        closeQuietly(transport);
      }
    } catch (Exception exception) {
      throw new BadRequestException("Unable to authenticate with the SMTP server");
    }
  }

  private Properties imapProperties(ImapSmtpStoredCredentials credentials) {
    Properties props = new Properties();
    String protocol = imapProtocol(credentials.imapSecurity());
    String prefix = "mail." + protocol + ".";
    props.put("mail.store.protocol", protocol);
    props.put(prefix + "connectiontimeout", timeoutMillis(properties.getConnectTimeout()));
    props.put(prefix + "timeout", timeoutMillis(properties.getReadTimeout()));
    props.put(prefix + "writetimeout", timeoutMillis(properties.getWriteTimeout()));
    props.put(prefix + "ssl.checkserveridentity", "true");
    if (credentials.imapSecurity() == MailSecurityMode.STARTTLS) {
      props.put("mail.imap.starttls.enable", "true");
      props.put("mail.imap.starttls.required", "true");
    }
    return props;
  }

  private Properties smtpProperties(ImapSmtpStoredCredentials credentials) {
    Properties props = new Properties();
    String protocol = smtpProtocol(credentials.smtpSecurity());
    String prefix = "mail." + protocol + ".";
    props.put("mail.transport.protocol", protocol);
    props.put(prefix + "auth", "true");
    props.put(prefix + "connectiontimeout", timeoutMillis(properties.getConnectTimeout()));
    props.put(prefix + "timeout", timeoutMillis(properties.getReadTimeout()));
    props.put(prefix + "writetimeout", timeoutMillis(properties.getWriteTimeout()));
    props.put(prefix + "ssl.checkserveridentity", "true");
    if (credentials.smtpSecurity() == MailSecurityMode.STARTTLS) {
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.starttls.required", "true");
    }
    return props;
  }

  private String resolveSentFolder(Store store, String requested) throws Exception {
    if (hasText(requested)) {
      Folder folder = store.getFolder(requested.trim());
      if (folder != null && folder.exists()) return folder.getFullName();
      throw new BadRequestException("Configured IMAP Sent folder does not exist");
    }

    Folder root = store.getDefaultFolder();
    Folder[] folders = root.list("*");
    for (String candidate : SENT_FOLDER_CANDIDATES) {
      for (Folder folder : folders) {
        if (candidate.equalsIgnoreCase(folder.getFullName())
            || candidate.equalsIgnoreCase(folder.getName())) {
          return folder.getFullName();
        }
      }
    }

    throw new BadRequestException(
        "Sent folder could not be detected. Enter the IMAP Sent folder name manually.");
  }

  private Message[] dateRangeMessages(
      Folder folder, EmailFolder emailFolder, LocalDate fromDate, LocalDate toDate)
      throws Exception {
    if (fromDate == null || toDate == null) {
      throw new BadRequestException("From and to dates are required");
    }
    if (fromDate.isAfter(toDate)) {
      throw new BadRequestException("From date must be before or equal to to date");
    }

    ZoneId zone = ZoneId.systemDefault();
    Date from = Date.from(fromDate.atStartOfDay(zone).toInstant());
    Date until = Date.from(toDate.plusDays(1).atStartOfDay(zone).toInstant());

    SearchTerm lower =
        emailFolder == EmailFolder.SENT
            ? new SentDateTerm(ComparisonTerm.GE, from)
            : new ReceivedDateTerm(ComparisonTerm.GE, from);
    SearchTerm upper =
        emailFolder == EmailFolder.SENT
            ? new SentDateTerm(ComparisonTerm.LT, until)
            : new ReceivedDateTerm(ComparisonTerm.LT, until);
    return folder.search(new AndTerm(lower, upper));
  }

  private Message[] latestMessages(Folder folder, Integer requestedCount) throws Exception {
    int count = requestedCount == null ? 100 : Math.max(1, requestedCount);
    int messageCount = folder.getMessageCount();
    if (messageCount <= 0) return new Message[0];
    int start = Math.max(1, messageCount - count + 1);
    return folder.getMessages(start, messageCount);
  }

  private List<MailboxMessage> snapshots(Folder folder, Message[] messages, EmailFolder emailFolder)
      throws Exception {
    if (!(folder instanceof UIDFolder uidFolder)) {
      throw new BadRequestException("IMAP server does not support UID-based synchronization");
    }

    if (messages.length > 0) {
      FetchProfile profile = new FetchProfile();
      profile.add(FetchProfile.Item.ENVELOPE);
      profile.add(FetchProfile.Item.FLAGS);
      profile.add(FetchProfile.Item.CONTENT_INFO);
      profile.add(UIDFolder.FetchProfileItem.UID);
      folder.fetch(messages, profile);
    }

    long uidValidity = uidFolder.getUIDValidity();
    List<MailboxMessage> result = new ArrayList<>(messages.length);
    for (Message message : messages) {
      long uid = uidFolder.getUID(message);
      if (uid <= 0) continue;
      result.add(snapshot(message, uidValidity, uid, emailFolder));
    }
    return result;
  }

  private MailboxMessage snapshot(
      Message message, long uidValidity, long uid, EmailFolder emailFolder) throws Exception {
    String internetMessageId = firstHeader(message, "Message-ID");
    String inReplyTo = firstHeader(message, "In-Reply-To");
    String references = firstHeader(message, "References");
    String broadcastCorrelationId = firstHeader(message, "X-Messaging-Broadcast-Recipient");
    String threadKey = threadKey(internetMessageId, inReplyTo, references, uidValidity, uid);
    Body body = extractBody(message);

    Instant sentAt =
        message.getSentDate() != null
            ? message.getSentDate().toInstant()
            : message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant()
                : Instant.now();

    return new MailboxMessage(
        uid,
        uidValidity,
        emailFolder.providerKey() + ":" + uidValidity + ":" + uid,
        trimToNull(internetMessageId),
        threadKey,
        firstAddress(message.getFrom()),
        firstAddress(message.getReplyTo()),
        addresses(message.getRecipients(Message.RecipientType.TO)),
        addresses(message.getRecipients(Message.RecipientType.CC)),
        addresses(message.getRecipients(Message.RecipientType.BCC)),
        trimToNull(inReplyTo),
        trimToNull(references),
        trimToNull(message.getSubject()),
        body.text(),
        body.html(),
        trimToNull(broadcastCorrelationId),
        sentAt,
        !message.isSet(Flags.Flag.SEEN),
        emailFolder);
  }

  private Body extractBody(Part part) throws Exception {
    if (part.isMimeType("message/delivery-status")) {
      Object content = part.getContent();
      return new Body(limit(content == null ? null : content.toString()), null);
    }
    if (part.isMimeType("text/plain")) {
      Object content = part.getContent();
      return new Body(limit(content == null ? null : content.toString()), null);
    }
    if (part.isMimeType("text/html")) {
      Object content = part.getContent();
      return new Body(null, limit(content == null ? null : content.toString()));
    }
    if (part.isMimeType("multipart/*")) {
      Object content = part.getContent();
      if (content instanceof Multipart multipart) {
        String text = null;
        String html = null;
        for (int i = 0; i < multipart.getCount(); i++) {
          Part child = multipart.getBodyPart(i);
          if (Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) continue;
          Body childBody = extractBody(child);
          if (text == null && hasText(childBody.text())) text = childBody.text();
          if (html == null && hasText(childBody.html())) html = childBody.html();
          if (text != null && html != null) break;
        }
        return new Body(text, html);
      }
    }
    if (part.isMimeType("message/rfc822")) {
      Object content = part.getContent();
      if (content instanceof Part nested) return extractBody(nested);
    }
    return new Body(null, null);
  }

  private ImapMessageRef parseMessageRef(String externalMessageId) {
    if (!hasText(externalMessageId)) {
      throw new BadRequestException("IMAP message identifier is missing");
    }
    String[] parts = externalMessageId.split(":", 3);
    if (parts.length != 3) {
      throw new BadRequestException("IMAP message identifier is invalid");
    }
    try {
      return new ImapMessageRef(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]));
    } catch (NumberFormatException exception) {
      throw new BadRequestException("IMAP message identifier is invalid");
    }
  }

  private String threadKey(
      String messageId, String inReplyTo, String references, long uidValidity, long uid) {
    if (hasText(references)) {
      String[] tokens = references.trim().split("\\s+");
      if (tokens.length > 0 && hasText(tokens[0])) return tokens[0];
    }
    if (hasText(inReplyTo)) return inReplyTo.trim();
    if (hasText(messageId)) return messageId.trim();
    return "imap-thread:" + uidValidity + ":" + uid;
  }

  private void ensureFolder(Folder folder, String name) throws Exception {
    if (folder == null || !folder.exists()) {
      throw new BadRequestException("IMAP folder does not exist: " + name);
    }
  }

  private String folderName(ImapSmtpStoredCredentials credentials, EmailFolder folder) {
    return folder == EmailFolder.SENT ? credentials.sentFolder() : "INBOX";
  }

  private String imapProtocol(MailSecurityMode security) {
    return security == MailSecurityMode.SSL_TLS ? "imaps" : "imap";
  }

  private String smtpProtocol(MailSecurityMode security) {
    return security == MailSecurityMode.SSL_TLS ? "smtps" : "smtp";
  }

  private String timeoutMillis(java.time.Duration duration) {
    return Long.toString(Math.max(1000, duration.toMillis()));
  }

  private void setRecipients(MimeMessage message, Message.RecipientType type, List<String> values)
      throws Exception {
    if (values.isEmpty()) return;
    InternetAddress[] addresses = new InternetAddress[values.size()];
    for (int i = 0; i < values.size(); i++) {
      InternetAddress address = new InternetAddress(values.get(i), true);
      address.validate();
      addresses[i] = address;
    }
    message.setRecipients(type, addresses);
  }

  private List<String> normalizeRecipients(List<String> values) {
    if (values == null) return List.of();
    Set<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (!hasText(value)) continue;
      try {
        InternetAddress address = new InternetAddress(value.trim(), true);
        address.validate();
        result.add(address.getAddress().toLowerCase(Locale.ROOT));
      } catch (Exception exception) {
        throw new BadRequestException("Invalid email recipient: " + value);
      }
    }
    return List.copyOf(result);
  }

  private String firstHeader(Message message, String name) throws Exception {
    String[] values = message.getHeader(name);
    return values == null || values.length == 0 ? null : values[0];
  }

  private String firstAddress(Address[] values) {
    List<String> addresses = addresses(values);
    return addresses.isEmpty() ? null : addresses.getFirst();
  }

  private List<String> addresses(Address[] values) {
    if (values == null || values.length == 0) return List.of();
    List<String> result = new ArrayList<>();
    for (Address value : values) {
      if (value instanceof InternetAddress internet && hasText(internet.getAddress())) {
        result.add(internet.getAddress().trim().toLowerCase(Locale.ROOT));
      }
    }
    return result;
  }

  private String limit(String value) {
    if (value == null || value.length() <= MAX_BODY_CHARS) return value;
    return value.substring(0, MAX_BODY_CHARS);
  }

  private String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void closeQuietly(jakarta.mail.Service service) {
    if (service == null) return;
    try {
      if (service.isConnected()) service.close();
    } catch (Exception ignored) {
      // Best-effort cleanup only.
    }
  }

  @FunctionalInterface
  public interface HistoryBatchConsumer {
    void accept(List<MailboxMessage> batch, int totalMessages);
  }

  public record TestedMailbox(String sentFolder) {}

  public record FolderCursor(long uidValidity, long lastUid) {}

  public record FolderDelta(long uidValidity, long lastUid, List<MailboxMessage> messages) {}

  public record MailboxMessage(
      long uid,
      long uidValidity,
      String externalMessageId,
      String internetMessageId,
      String threadKey,
      String from,
      String replyTo,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String inReplyTo,
      String references,
      String subject,
      String bodyText,
      String bodyHtml,
      String broadcastCorrelationId,
      Instant sentAt,
      boolean unread,
      EmailFolder folder) {

    public MailboxMessage(
        long uid,
        long uidValidity,
        String externalMessageId,
        String internetMessageId,
        String threadKey,
        String from,
        String replyTo,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String inReplyTo,
        String references,
        String subject,
        String bodyText,
        String bodyHtml,
        Instant sentAt,
        boolean unread,
        EmailFolder folder) {
      this(
          uid,
          uidValidity,
          externalMessageId,
          internetMessageId,
          threadKey,
          from,
          replyTo,
          to,
          cc,
          bcc,
          inReplyTo,
          references,
          subject,
          bodyText,
          bodyHtml,
          null,
          sentAt,
          unread,
          folder);
    }
  }

  public record SentMessage(
      String messageId,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String subject,
      Instant sentAt) {}

  private record ImapMessageRef(String folderKey, long uidValidity, long uid) {}

  private record Body(String text, String html) {}
}
