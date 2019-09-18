package io.forsta.librelay;

import android.support.annotation.NonNull;

import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);

  MessageRecord getMessageRecord();
}
