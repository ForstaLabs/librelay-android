package io.forsta.librelay.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.Util;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;


public class RecipientPreferenceDatabase extends DbBase {

  private static final String TAG = RecipientPreferenceDatabase.class.getSimpleName();
  private static final String RECIPIENT_PREFERENCES_URI = "content://io.forsta.librelay.provider.database/recipients/";

  private static final String TABLE_NAME = "recipient_preferences";
  private static final String ID = "_id";
  private static final String RECIPIENT_ID = "recipient_id";
  private static final String BLOCK = "block";
  private static final String NOTIFICATION = "notification";
  private static final String VIBRATE = "vibrate";
  private static final String MUTE_UNTIL = "mute_until";
  private static final String COLOR = "color";

  public enum VibrateState {
    DEFAULT(0), ENABLED(1), DISABLED(2);

    private final int id;

    VibrateState(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static VibrateState fromId(int id) {
      return values()[id];
    }
  }

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          RECIPIENT_ID + " INTEGER UNIQUE, " +
          BLOCK + " INTEGER DEFAULT 0," +
          NOTIFICATION + " TEXT DEFAULT NULL, " +
          VIBRATE + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
          MUTE_UNTIL + " INTEGER DEFAULT 0, " +
          COLOR + " TEXT DEFAULT NULL);";

  public RecipientPreferenceDatabase(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public Cursor getBlocked() {
    SQLiteDatabase database = dbHelper.getReadableDatabase();

    Cursor cursor = database.query(TABLE_NAME, new String[] {ID, RECIPIENT_ID}, BLOCK + " = 1",
                                   null, null, null, null, null);
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(RECIPIENT_PREFERENCES_URI));

    return cursor;
  }

  public BlockedReader readerForBlocked(Cursor cursor) {
    return new BlockedReader(context, cursor);
  }

  public Optional<RecipientPreferences> getRecipientsPreferences(@NonNull long[] recipients) {
    Arrays.sort(recipients);

    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, RECIPIENT_ID + " = ?",
                              new String[] {Util.join(recipients, " ")},
                              null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        boolean blocked               = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK))                == 1;
        String  notification          = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
        int     vibrateState          = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
        long    muteUntil             = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
        String  serializedColor       = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
        Uri     notificationUri       = notification == null ? null : Uri.parse(notification);
        MaterialColor color;

        try {
          color = serializedColor == null ? null : MaterialColor.fromSerialized(serializedColor);
        } catch (MaterialColor.UnknownColorException e) {
          Log.w(TAG, e);
          color = null;
        }

        Log.w(TAG, "Muted until: " + muteUntil);

        return Optional.of(new RecipientPreferences(blocked, muteUntil,
                                                     VibrateState.fromId(vibrateState),
                                                     notificationUri, color));
      }

      return Optional.absent();
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public void setColor(Recipients recipients, MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    updateOrInsert(recipients, values);
  }

  public void setBlocked(Recipients recipients, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(recipients, values);
  }

  public void setRingtone(Recipients recipients, @Nullable Uri notification) {
    ContentValues values = new ContentValues();
    values.put(NOTIFICATION, notification == null ? null : notification.toString());
    updateOrInsert(recipients, values);
  }

  public void setVibrate(Recipients recipients, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(VIBRATE, enabled.getId());
    updateOrInsert(recipients, values);
  }

  public void setMuted(Recipients recipients, long until) {
    Log.w(TAG, "Setting muted until: " + until);
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    updateOrInsert(recipients, values);
  }

  private void updateOrInsert(Recipients recipients, ContentValues contentValues) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();

    database.beginTransaction();

    int updated = database.update(TABLE_NAME, contentValues, RECIPIENT_ID + " = ?",
                                  new String[] {String.valueOf(recipients.getSortedAddresses())});

    if (updated < 1) {
      contentValues.put(RECIPIENT_ID, recipients.getSortedAddresses());
      database.insert(TABLE_NAME, null, contentValues);
    }

    database.setTransactionSuccessful();
    database.endTransaction();

    context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
  }

  public static class RecipientPreferences {
    private final boolean       blocked;
    private final long          muteUntil;
    private final VibrateState  vibrateState;
    private final Uri           notification;
    private final MaterialColor color;

    public RecipientPreferences(boolean blocked, long muteUntil,
                                @NonNull VibrateState vibrateState,
                                @Nullable Uri notification,
                                @Nullable MaterialColor color)
    {
      this.blocked               = blocked;
      this.muteUntil             = muteUntil;
      this.vibrateState          = vibrateState;
      this.notification          = notification;
      this.color                 = color;
    }

    public @Nullable MaterialColor getColor() {
      return color;
    }

    public boolean isBlocked() {
      return blocked;
    }

    public long getMuteUntil() {
      return muteUntil;
    }

    public @NonNull VibrateState getVibrateState() {
      return vibrateState;
    }

    public @Nullable Uri getRingtone() {
      return notification;
    }
  }

  public static class BlockedReader {

    private final Context context;
    private final Cursor cursor;

    public BlockedReader(Context context, Cursor cursor) {
      this.context = context;
      this.cursor  = cursor;
    }

    public @NonNull
    Recipient getCurrent() {
      long recipientId = cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID));
      return RecipientFactory.getRecipientForId(context, recipientId, false);
    }

    public @Nullable Recipient getNext() {
      if (!cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }
  }
}
