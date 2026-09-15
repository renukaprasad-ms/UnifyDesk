package com.messaging.media.service;

import com.messaging.media.entity.Media;
import com.messaging.media.exception.MediaException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MediaAuthorizationService {

  public void requireOwner(Media media, long userId) {
    if (!media.getOwnerUserId().equals(userId)) {
      throw new MediaException(HttpStatus.FORBIDDEN, "MEDIA_ACCESS_DENIED");
    }
  }
}
