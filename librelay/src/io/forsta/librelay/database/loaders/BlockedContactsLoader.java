package io.forsta.librelay.database.loaders;

import android.content.Context;
import android.database.Cursor;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DbFactory.getRecipientPreferenceDatabase(getContext())
                          .getBlocked();
  }

}
