package io.forsta.librelay.database.loaders;

import android.content.Context;
import android.database.Cursor;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.util.AbstractCursorLoader;

public class ConversationListLoader extends AbstractCursorLoader {

  private final String filter;
  private final boolean archived;
  private final boolean showAnnouncements;

  public ConversationListLoader(Context context, String filter, boolean archived, boolean showAnnouncements) {
    super(context);
    this.filter   = filter;
    this.archived = archived;
    this.showAnnouncements = showAnnouncements;
  }

  @Override
  public Cursor getCursor() {
    if      (filter != null && filter.trim().length() != 0) return getFilteredConversationList(filter);
    else if (!showAnnouncements) return getConversationListWithoutAnnouncements();
    else if (!archived)                                     return getConversationList();
    else                                                    return getArchivedConversationList();
  }

  private Cursor getConversationListWithoutAnnouncements() {
    return DbFactory.getThreadDatabase(context).getConversationListWithoutAnnouncements();
  }

  private Cursor getConversationList() {
    return DbFactory.getThreadDatabase(context).getConversationList();
  }

  private Cursor getArchivedConversationList() {
    return DbFactory.getThreadDatabase(context).getArchivedConversationList();
  }

  private Cursor getFilteredConversationList(String filter) {
    return DbFactory.getThreadDatabase(context).getFilteredConversationList(filter);
  }
}
