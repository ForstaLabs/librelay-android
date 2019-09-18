package io.forsta.librelay.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import io.forsta.librelay.database.DbFactory;

public class IdentityLoader extends CursorLoader {

  private final Context context;

  public IdentityLoader(Context context) {
    super(context);
    this.context      = context.getApplicationContext();
  }

  @Override
  public Cursor loadInBackground() {
    return DbFactory.getIdentities(context).getIdentities();
  }

}
