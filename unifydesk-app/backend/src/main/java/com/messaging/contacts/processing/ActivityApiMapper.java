package com.messaging.contacts.processing;

import com.messaging.contacts.processing.ActivityApiModel.ActivityItem;
import com.messaging.contacts.processing.ActivityApiModel.ActivityPage;
import org.springframework.stereotype.Component;

@Component
public class ActivityApiMapper {

  public ActivityPage toApi(ContactActivityService.ActivityPage page) {
    return new ActivityPage(
        page.items().stream()
            .map(
                item ->
                    new ActivityItem(
                        Long.toString(item.id()),
                        item.type(),
                        item.title(),
                        item.summary(),
                        item.occurredAt(),
                        item.href()))
            .toList(),
        page.nextCursor(),
        page.hasMore());
  }
}
