package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

class MailSentArchiveStoreTests {
  @Test
  void oversizedMimeIsRejectedBeforeDiskOrDatabaseAccess() {
    var archive=new MailSentArchiveStore(null,null);
    assertThrows(ServiceUnavailableException.class,
        ()->archive.prepare(1L,2L,3L,new byte[MailSentArchiveStore.MAX_SIGNED_MIME+1]));
    assertThrows(ServiceUnavailableException.class,
        ()->archive.prepare(1L,2L,3L,new byte[0]));
  }
}
