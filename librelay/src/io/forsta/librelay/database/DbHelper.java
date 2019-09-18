package io.forsta.librelay.database;

/**
 * Created by jlewis on 3/23/17.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by john on 3/14/2017.
 */

public class DbHelper extends SQLiteOpenHelper {
  private static final String DB_NAME = "librelay.db";
  private static final int VERSION = 1;

  public DbHelper(Context context) {
    super(context, DB_NAME, null, VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(MessageDatabase.CREATE_TABLE);
    db.execSQL(AttachmentDatabase.CREATE_TABLE);
    db.execSQL(ThreadDatabase.CREATE_TABLE);
    db.execSQL(DraftDatabase.CREATE_TABLE);
    db.execSQL(PushDatabase.CREATE_TABLE);
    db.execSQL(GroupDatabase.CREATE_TABLE);
    db.execSQL(RecipientPreferenceDatabase.CREATE_TABLE);
    db.execSQL(ThreadPreferenceDatabase.CREATE_TABLE);
    db.execSQL(MessageReceiptsDatabase.CREATE_TABLE);

    db.execSQL(Contacts.CREATE_TABLE);
    db.execSQL(Identities.CREATE_TABLE);
    db.execSQL(SignedPrekeys.CREATE_TABLE);
    db.execSQL(Prekeys.CREATE_TABLE);
    db.execSQL(Sessions.CREATE_TABLE);

    executeStatements(db, MessageDatabase.CREATE_INDEXS);
    executeStatements(db, MessageReceiptsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, Contacts.CREATE_INDEXS);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }
}
