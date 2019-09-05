package io.forsta.librelay.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.util.Base64;

public class Identities extends DbBase {
  private static final String TAG = Identities.class.getSimpleName();

  private static final Uri CHANGE_URI = Uri.parse("content://" + BuildConfig.FORSTA_PROVIDER + ".database/identities");

  private static final String TABLE_NAME           = "identities";
  private static final String ID                   = "_id";
  private static final String ADDRESS              = "address";
  private static final String IDENTITY_KEY         = "key";
  private static final String TIMESTAMP            = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " INTEGER PRIMARY KEY, " +
      ADDRESS + " TEXT UNIQUE, " +
      IDENTITY_KEY + " TEXT, " +
      TIMESTAMP + " INTEGER DEFAULT 0);";

  protected Identities(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public Cursor getIdentities() {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    return database.query(TABLE_NAME, null, null, null, null, null, null);
  }

  public Optional<IdentityRecord> getIdentity(String address) {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?",
          new String[] {address}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Optional.of(getIdentityRecord(cursor));
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    } finally {
      if (cursor != null) cursor.close();
    }

    return Optional.absent();
  }

  public void saveIdentity(Context context, long recipientId, IdentityKey identityKey) {
    String address = RecipientFactory.getRecipientForId(context, recipientId, false).getAddress();
    saveIdentity(address, identityKey);
  }

  public void saveIdentity(String address, IdentityKey identityKey)
  {
    SQLiteDatabase database          = dbHelper.getWritableDatabase();
    String         identityKeyString = Base64.encodeBytes(identityKey.serialize());

    ContentValues contentValues = new ContentValues();
    contentValues.put(ADDRESS, address);
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(TIMESTAMP, System.currentTimeMillis());

    database.replace(TABLE_NAME, null, contentValues);

    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  private IdentityRecord getIdentityRecord(@NonNull Cursor cursor) throws IOException, InvalidKeyException {
    String      address             = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
    String      serializedIdentity  = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
    long        timestamp           = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
    IdentityKey identity            = new IdentityKey(Base64.decode(serializedIdentity), 0);

    return new IdentityRecord(address, identity, timestamp);
  }

  public void deleteIdentity(long id) {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID + " = ?", new String[] {id+""});

    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  public boolean isValidIdentity(String address,
                                 IdentityKey theirIdentity)
  {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?",
          new String[] {address}, null, null,null);

      if (cursor != null && cursor.moveToFirst()) {
        String      serializedIdentity = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
        IdentityKey ourIdentity        = new IdentityKey(Base64.decode(serializedIdentity), 0);

        return ourIdentity.equals(theirIdentity);
      } else {
        return true;
      }
    } catch (IOException e) {
      Log.w("Identities", e);
      return false;
    } catch (InvalidKeyException e) {
      Log.w("Identities", e);
      return false;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public class Reader {
    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public IdentityRecord getCurrent() {

      try {
        String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
        String      identityKeyString = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
        IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyString), 0);

        return new IdentityRecord(address, identityKey, System.currentTimeMillis());
      } catch (InvalidKeyException e) {
        Log.e(TAG, "Identities table exception");
        e.printStackTrace();
      } catch (IOException e) {
        Log.e(TAG, "Identities table exception");
        e.printStackTrace();
      }
      return null;
    }

    public void close() {
      cursor.close();
    }
  }

  public static class IdentityRecord {

    private final String        address;
    private final IdentityKey identitykey;
    private final long           timestamp;

    private IdentityRecord(String address,
                           IdentityKey identitykey,
                           long timestamp)
    {
      this.address             = address;
      this.identitykey         = identitykey;
      this.timestamp           = timestamp;
    }

    public String getAddress() {
      return address;
    }

    public IdentityKey getIdentityKey() {
      return identitykey;
    }

    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public String toString() {
      return "{address: " + address + ", identityKey: " + identitykey + "}";
    }

  }
}

