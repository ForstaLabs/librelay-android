package io.forsta.librelay.database.loaders;

import android.content.Context;
import android.database.Cursor;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.util.AbstractCursorLoader;

public class ConversationLoader extends AbstractCursorLoader {
  private final long threadId;
  private       long limit;

  public ConversationLoader(Context context, long threadId, long limit) {
    super(context);
    this.threadId = threadId;
    this.limit  = limit;
  }

  public boolean hasLimit() {
    return limit > 0;
  }

  @Override
  public Cursor getCursor() {
    return DbFactory.getMessageDatabase(context).getConversation(threadId, limit);
  }
}
