package io.forsta.librelay;

import android.support.annotation.NonNull;

import io.forsta.librelay.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode);
}
