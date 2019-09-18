package io.forsta.librelay.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.whispersystems.libsignal.state.SessionRecord;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Sessions extends DbBase {
  public static final String TAG = Sessions.class.getSimpleName();

  public static final String TABLE_NAME = "sessions";
  private static final String ID      = "_id";
  public static final  String ADDRESS = "address";
  public static final  String DEVICE  = "device";
  public static final  String RECORD  = "record";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      "(" + ID + " INTEGER PRIMARY KEY, " + ADDRESS + " TEXT NOT NULL, " +
      DEVICE + " INTEGER NOT NULL, " + RECORD + " BLOB NOT NULL, " +
      "UNIQUE(" + ADDRESS + "," + DEVICE + ") ON CONFLICT REPLACE);";

  protected Sessions(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public void store(@NonNull String address, int deviceId, @NonNull SessionRecord record) {
    ContentValues values = new ContentValues();
    values.put(ADDRESS, address);
    values.put(DEVICE, deviceId);
    values.put(RECORD, record.serialize());

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public @Nullable SessionRecord load(@NonNull String address, int deviceId) {
    SQLiteDatabase db = dbHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{RECORD},
        ADDRESS + " = ? AND " + DEVICE + " = ?",
        new String[] {address, String.valueOf(deviceId)},
        null, null, null))
    {
      if (cursor != null && cursor.moveToFirst()) {
        try {
          return new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  public @NonNull List<SessionRow> getAllFor(@NonNull String address) {
    SQLiteDatabase db = dbHelper.getReadableDatabase();

    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, null,
        ADDRESS + " = ?",
        new String[] {address},
        null, null, null))
    {
      while (cursor != null && cursor.moveToNext()) {
        try {
          results.add(new SessionRow(address,
              cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE)),
              new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<SessionRow> getAll() {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        try {
          results.add(new SessionRow(cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)),
              cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE)),
              new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<Integer> getDeviceSessions(@NonNull String address) {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    List<Integer>  results  = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {DEVICE},
        ADDRESS + " = ?",
        new String[] {address},
        null, null, null))
    {
      while (cursor != null && cursor.moveToNext()) {
        int device = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE));
        results.add(device);
      }
    }

    return results;
  }

  public void delete(@NonNull String address, int deviceId) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();

    db.delete(TABLE_NAME, ADDRESS + " = ? AND " + DEVICE + " = ?",
        new String[] {address, String.valueOf(deviceId)});
  }

  public void deleteAllFor(@NonNull String address) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.delete(TABLE_NAME, ADDRESS + " = ?", new String[] {address});
  }

  public static final class SessionRow {
    private final String       address;
    private final int           deviceId;
    private final SessionRecord record;

    public SessionRow(String address, int deviceId, SessionRecord record) {
      this.address  = address;
      this.deviceId = deviceId;
      this.record   = record;
    }

    public String getAddress() {
      return address;
    }

    public int getDeviceId() {
      return deviceId;
    }

    public SessionRecord getRecord() {
      return record;
    }
  }
}
