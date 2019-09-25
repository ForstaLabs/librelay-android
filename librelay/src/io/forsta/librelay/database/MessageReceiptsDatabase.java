/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.librelay.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import io.forsta.librelay.database.model.MessageReceipt;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;

import java.util.LinkedList;
import java.util.List;

import io.forsta.librelay.util.TextSecurePreferences;

public class MessageReceiptsDatabase extends DbBase {

  private static final String TAG = MessageReceiptsDatabase.class.getSimpleName();

  private static final String TABLE_NAME = "message_receipts";
  private static final String ID = "_id";
  public static final String MESSAGE_ID = "message_id";
  public static final String ADDRESS = "address";
  public static final String TIMESTAMP = "timestamp";
  public static final String READ = "read";
  public static final String DELIVERED = "delivered";
  public static final String FAILED = "failed"; //TODO Store timestamp for each event in the field.

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
      MESSAGE_ID + " INTEGER, " +  ADDRESS + " TEXT, " + READ + " INTEGER DEFAULT 0, " + DELIVERED + " INTEGER DEFAULT 0, " + FAILED + " INTEGER DEFAULT 0, "+ TIMESTAMP + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
      "CREATE INDEX IF NOT EXISTS message_receipts_message_id_index ON " + TABLE_NAME + " (" + MESSAGE_ID + ");",
      "CREATE INDEX IF NOT EXISTS message_receipts_address_index ON " + TABLE_NAME + " (" + ADDRESS + ");",
  };

  public MessageReceiptsDatabase(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  private void insertAddress(long messageId, @NonNull String value) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(MESSAGE_ID, messageId);
    contentValues.put(ADDRESS, value);
    database.insert(TABLE_NAME, null, contentValues);
  }

  public void insertAddressesForId(long messageId, @NonNull List<String> addresses) {
    for (String address : addresses) {
      insertAddress(messageId, address);
    }
  }

  public List<String> getAddressesListForId(long messageId) {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor         cursor   = null;
    List<String>   to       = new LinkedList<>();

    try {
      cursor = database.query(TABLE_NAME, null, MESSAGE_ID + " = ?", new String[] {messageId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
        to.add(address);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return to;
  }

  public List<MessageReceipt> getReceiptsForMessageId(long messageId) {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor cursor = null;
    List<MessageReceipt> receipts = new LinkedList<>();

    try {
      cursor = database.query(TABLE_NAME, null, MESSAGE_ID + " = ?", new String[] {messageId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String address = cursor.getString(cursor.getColumnIndex(ADDRESS));
        int delivered = cursor.getInt(cursor.getColumnIndex(DELIVERED));
        int read = cursor.getInt(cursor.getColumnIndex(READ));
        int failed = cursor.getInt(cursor.getColumnIndex(FAILED));
        long timeStamp = cursor.getLong(cursor.getColumnIndex(TIMESTAMP));
        MessageReceipt receipt = new MessageReceipt(messageId, address, delivered, read, failed);
        receipts.add(receipt);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return receipts;
  }

  public Recipients getRecipientsForId(long messageId) {
    List<String>    addresses = getAddressesListForId(messageId);
    String localAddress = TextSecurePreferences.getLocalAddress(context);
    if (addresses.contains(localAddress)) {
      addresses.remove(localAddress);
    }

    return RecipientFactory.getRecipientsFromStrings(context, addresses, false);
  }

  public void updateDelivered(long messageId, String address, long timeStamp) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(TIMESTAMP, timeStamp);
    contentValues.put(DELIVERED, 1);
    database.update(TABLE_NAME, contentValues, MESSAGE_ID + " = ? AND " + ADDRESS + " = ?", new String[] {messageId+"", address});
  }

  public void updateRead(long messageId, String address) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);
    database.update(TABLE_NAME, contentValues, MESSAGE_ID + " = ? AND " + ADDRESS + " = ?", new String[] {messageId+"", address});
  }

  public void updateFailed(long messageId, String address) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(FAILED, 1);
    database.update(TABLE_NAME, contentValues, MESSAGE_ID + " = ? AND " + ADDRESS + " = ?", new String[] {messageId+"", address});
  }

  public void updateUnregisteredUser(long messageId, String address) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(FAILED, 2);
    database.update(TABLE_NAME, contentValues, MESSAGE_ID + " = ? AND " + ADDRESS + " = ?", new String[] {messageId+"", address});
  }

  public void deleteAddressesForId(long messageId) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.delete(TABLE_NAME, MESSAGE_ID + " = ?", new String[] {messageId+""});
  }

  public void deleteAllAddresses() {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  public Cursor getAllAddresses() {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    return database.query(TABLE_NAME, null, null, null, null, null, null);
  }
}
