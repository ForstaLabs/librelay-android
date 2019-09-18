package io.forsta.librelay.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.util.Set;

import io.forsta.librelay.BuildConfig;

/**
 * Created by jlewis on 4/6/17.
 */

public abstract class DbBase {
  protected SQLiteOpenHelper dbHelper;
  protected Context context;
  protected static final String ID_WHERE = "_id = ?";
  public static final String CONVERSATION_URI = "content://" + BuildConfig.FORSTA_PROVIDER + ".database/conversation/";
  private static final String CONVERSATION_LIST_URI = "content://" + BuildConfig.FORSTA_PROVIDER + ".database/conversation-list";

  protected DbBase(Context context, DbHelper dbHelper) {
    this.dbHelper = dbHelper;
    this.context = context;
  }

  protected int updateRecord(String table, String id, ContentValues values) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    int result = db.update(table, values, " _id= ? ", new String[] {id});
    return result;
  }

  protected long addRecord(String table, ContentValues values) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    long result = db.insert(table, null, values);
    return result;
  }

  protected int removeRecord(String table, String id) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    String[] args = new String[] {id};
    int result = db.delete(table, "_id = ?", args);
    return result;
  }

  protected Cursor getRecords(String table, String[] columns, String selection, String[] selectionArgs, String sort) throws Exception {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    return db.query(table, columns, selection, selectionArgs, null, null, sort);
  }

  protected void removeAll(String table) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.delete(table, null, null);
  }

  protected void notifyConversationListeners(Set<Long> threadIds) {
    for (long threadId : threadIds)
      notifyConversationListeners(threadId);
  }

  protected void notifyConversationListeners(long threadId) {
    context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_URI + threadId), null);
  }

  protected void notifyConversationListListeners() {
    context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_LIST_URI), null);
  }

  protected void setNotifyConverationListeners(Cursor cursor, long threadId) {
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_URI + threadId));
  }

  protected void setNotifyConverationListListeners(Cursor cursor) {
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_LIST_URI));
  }

  public void reset(DbHelper databaseHelper) {
    this.dbHelper = databaseHelper;
  }
}
