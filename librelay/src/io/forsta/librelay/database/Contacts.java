package io.forsta.librelay.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;

/**
 * Created by jlewis on 3/23/17.
 */

public class Contacts extends DbBase {
  public static final String TAG = Contacts.class.getSimpleName();

  private static final String CONTACT_MIMETYPE = "vnd.android.cursor.item/vnd.io.forsta.librelay.contact";
  private static final String SYNC             = "__TS";
  public static final String ID_COLUMN           = "_id";
  public static final String NAME_COLUMN         = "name";
  public static final String NUMBER_COLUMN       = "number";
  public static final String NUMBER_TYPE_COLUMN  = "number_type";
  public static final String LABEL_COLUMN        = "label";
  public static final String CONTACT_TYPE_COLUMN = "contact_type";

  public static final int NORMAL_TYPE = 0;
  public static final int ATLAS_TYPE = 1;
  public static final int ATLAS_GROUP_TYPE = 2;

  public static final String TABLE_NAME = "contacts";
  public static final String ID = "_id";
  public static final String NAME = "name";
  public static final String EMAIL = "email";
  public static final String NUMBER = "number";
  public static final String USERNAME = "username";
  public static final String AVATAR = "avatar";
  public static final String ADDRESS = "uid";
  public static final String TAGID = "tagid";
  public static final String SLUG = "slug";
  public static final String ORGID = "orgid";
  public static final String ORGSLUG = "org_slug";
  public static final String DATE = "date";
  public static final String TSREGISTERED = "tsregistered";
  public static final String ISACTIVE = "isactive";
  public static final String ISMONITOR = "ismonitor";
  public static final String USERTYPE = "type";

  public static final String CREATE_TABLE = "create table " +
      TABLE_NAME + "(" +
      ID + " integer primary key autoincrement, " +
      NAME + ", " +
      EMAIL + ", " +
      NUMBER + ", " +
      USERNAME + ", " +
      AVATAR + ", " +
      ADDRESS + ", " +
      TAGID + ", " +
      SLUG + ", " +
      ORGID + ", " +
      ORGSLUG + ", " +
      DATE + ", " +
      TSREGISTERED + " integer default 0, " +
      ISACTIVE + " integer default 0, " +
      ISMONITOR + " integer default 0, " +
      USERTYPE + ", " +
      "CONSTRAINT item_address_unique UNIQUE (" + ADDRESS + ")" +
      ")";

  public static final String[] CREATE_INDEXS = {
      "CREATE INDEX IF NOT EXISTS contacts_address_index ON " + TABLE_NAME + " (" + ADDRESS + ");",
      "CREATE INDEX IF NOT EXISTS contacts_tagslug_index ON " + TABLE_NAME + " (" + SLUG + ");",
      "CREATE INDEX IF NOT EXISTS contacts_name_index ON " + TABLE_NAME + " (" + NAME + ");"
  };

  public static String[] allColumns = {
      ID,
      NAME,
      EMAIL,
      NUMBER,
      USERNAME,
      AVATAR,
      ADDRESS,
      TAGID,
      SLUG,
      ORGID,
      ORGSLUG,
      DATE,
      TSREGISTERED,
      ISACTIVE,
      ISMONITOR,
      USERTYPE
  };

  public Contacts(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public void removeAll() {
    removeAll(TABLE_NAME);
  }

  public Cursor getContactByAddress(String address) {
    try {
      return getRecords(TABLE_NAME, null, ADDRESS + " = ?", new String[] {address}, ADDRESS);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public Long getIdFromAddress(String address) {
    Long id = null;
    try {
      Cursor cursor = getRecords(TABLE_NAME, null, ADDRESS + " = ?", new String[] {address}, ADDRESS);
      if (cursor != null && cursor.moveToNext()) {
        id = cursor.getLong(cursor.getColumnIndex(ID));
        cursor.close();
      } else {
        ContentValues values = new ContentValues();
        values.put(ADDRESS, address);
        id = dbHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return id;
  }

  public String getAddressFromId(long id) {
    String address = null;
    try {
      Cursor cursor = getRecords(TABLE_NAME, null, ID + " = ?", new String[] {id+""}, ADDRESS);
      if (cursor != null && cursor.moveToNext()) {
        address = cursor.getString(cursor.getColumnIndex(ADDRESS));
      }
      cursor.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return address;
  }

  public List<String> getAddressesFromIds(List<String> ids) {
    List<String> addresses = new ArrayList<>();
    String query = "";
    String queryNumbers = TextUtils.join("','", ids);
    query = ADDRESS + " IN ('" + queryNumbers + "')";
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, query, null, null);
      while (c.moveToNext()) {
        addresses.add(c.getString(c.getColumnIndex(ADDRESS)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return addresses;
  }

  public Set<String> getNumbers() {
    Set<String> numbers = new HashSet<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NUMBER);
      while (c.moveToNext()) {
        numbers.add(c.getString(c.getColumnIndex(NUMBER)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return numbers;
  }

  public Set<String> getAddresses() {
    Set<String> addresses = new HashSet<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, ADDRESS);
      while (c.moveToNext()) {
        addresses.add(c.getString(c.getColumnIndex(ADDRESS)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return addresses;
  }

  public Map<String, String> getUids() {
    Map<String, String> ids = new HashMap<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, ADDRESS);
      while (c.moveToNext()) {
        ids.put(c.getString(c.getColumnIndex(ADDRESS)), c.getString(c.getColumnIndex(ID)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return ids;
  }

  public void updateUsers(List<AtlasUser> users, boolean removeExisting) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    Map<String, String> uids = getUids();

    db.beginTransaction();
    try {
      for (AtlasUser user : users) {
        ContentValues values = new ContentValues();
        values.put(Contacts.ADDRESS, user.uid);
        values.put(Contacts.TAGID, user.tag_id);
        values.put(Contacts.SLUG, user.slug);
        values.put(Contacts.ADDRESS, user.uid);
        values.put(Contacts.AVATAR, user.avatar);
        values.put(Contacts.NAME, user.name);
        values.put(Contacts.ORGID, user.org_id);
        values.put(Contacts.ORGSLUG, user.org_slug);
        values.put(Contacts.NUMBER, user.phone);
        values.put(Contacts.USERNAME, user.username);
        values.put(Contacts.EMAIL, user.email);
        values.put(Contacts.ISACTIVE, user.isActive);
        values.put(Contacts.ISMONITOR, user.isMonitor);
        values.put(Contacts.USERTYPE, user.type.toString());
        if (uids.containsKey(user.uid)) {
          String id = uids.get(user.getUid());
          if (TextUtils.isEmpty(user.getUid())) {
            Log.w(TAG, "Existing user with empty ADDRESS!: " + user.slug);
            db.delete(TABLE_NAME, ID + " = ?", new String[] { id });
          } else {
            db.update(TABLE_NAME, values, ID + " = ?", new String[] { id });
          }
        } else {
          if (TextUtils.isEmpty(user.getUid())) {
            Log.w(TAG, "New user with empty ADDRESS!: " + user.slug);
          } else {
            db.insert(TABLE_NAME, null, values);
          }
        }
        uids.remove(user.uid);
      }
      db.setTransactionSuccessful();
      RecipientFactory.clearCache(context);
    } finally {
      db.endTransaction();
    }
    if (removeExisting && users.size() > 0) {
      Log.w(TAG, "Reseting directory. Removing " + uids.size() + " entries.");
      db.beginTransaction();
      try {
        for (String uid : uids.keySet()) {
          db.delete(TABLE_NAME, ADDRESS + "=?", new String[] { uid });
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    }
  }

  public void setInactiveAddresses(List<String> addresses) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (String uid : addresses) {
        ContentValues values = new ContentValues();
        values.put(ISACTIVE, false);
        db.update(TABLE_NAME, values, ADDRESS + "=?", new String[] { uid });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private Cursor getContacts(String filter) {
    String queryFilter = "(" + ISACTIVE + " = 1 AND " + ISMONITOR + " = 0 AND " + USERTYPE + " = 'PERSON')";

    String[] queryValues = null;
    if (filter != null && filter.length() > 0) {
      String user = filter;
      String org = "";
      String[] parts = filter.split(":");
      if (parts.length > 0) {
        user = parts[0];
        if (parts.length > 1) {
          org = parts[1];
        }
      }
      if (!TextUtils.isEmpty(org)) {
        queryFilter += " AND (" + NAME + " LIKE ? OR " + SLUG + " LIKE ? AND " + ORGSLUG + " LIKE ? OR " + NUMBER + " LIKE ? OR " + EMAIL + " LIKE ?)";
        queryValues = new String[] { "%" + user + "%", "%" + user + "%", "%" + org + "%", "%" + user + "%", "%" + user + "%" };
      } else {
        queryFilter += " AND (" + NAME + " LIKE ? OR " + SLUG + " LIKE ? OR " + NUMBER + " LIKE ? OR " + EMAIL + " LIKE ?)";
        queryValues = new String[] { "%" + user + "%", "%" + user + "%", "%" + user + "%", "%" + user + "%" };
      }
    }

    try {
      return getRecords(TABLE_NAME, allColumns, queryFilter, queryValues, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public List<Recipient> getRecipients(String filter) {
    List<Recipient> recipients = new LinkedList<>();
    Cursor cursor = getContacts(filter);
    try {
      while (cursor != null && cursor.moveToNext()) {
        recipients.add(new Recipient(cursor));
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return recipients;
  }

  public Recipient getRecipientByTag(String tag) {
    Recipient user = null;
    String[] splitTag = tag.split(":");
    try {
      Cursor cursor = null;
      if(splitTag.length == 1) {
        cursor = getRecords(TABLE_NAME, null, SLUG + " = ?", splitTag, SLUG);
      } else if(splitTag.length == 2) {
        cursor = getRecords(TABLE_NAME, null, SLUG + " = ?" + " AND " + ORGSLUG + " = ?", splitTag, SLUG);
      }
      if(cursor != null && cursor.moveToNext()) {
        user = new Recipient(cursor);
      }
      cursor.close();
    }catch(Exception e) {
      e.printStackTrace();
    }
    return user;
  }

    public Recipient getRecipient(String address) {
        Recipient user = null;
        Cursor cursor = null;
        try {
            cursor = getRecords(TABLE_NAME, null, ADDRESS + " = ?", new String[] {address}, ADDRESS);
            if(cursor != null && cursor.moveToNext()) {
                user = new Recipient(cursor);
            }
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return user;
    }

  public Cursor getAll() {
    try {
      return getRecords(TABLE_NAME, allColumns, null, null, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public @NonNull
  Cursor querySystemContacts(String filter) {

    Uri uri;

    if (!TextUtils.isEmpty(filter)) {
      uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(filter));
    } else {
      uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    }

    uri = uri.buildUpon().appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").build();

    String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone._ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.LABEL};

    String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

    Map<String, String> projectionMap = new HashMap<String, String>() {{
      put(ID_COLUMN, ContactsContract.CommonDataKinds.Phone._ID);
      put(NAME_COLUMN, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
      put(NUMBER_COLUMN, ContactsContract.CommonDataKinds.Phone.NUMBER);
      put(NUMBER_TYPE_COLUMN, ContactsContract.CommonDataKinds.Phone.TYPE);
      put(LABEL_COLUMN, ContactsContract.CommonDataKinds.Phone.LABEL);
    }};

    String excludeSelection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " NOT IN (" +
        "SELECT data.contact_id FROM raw_contacts, view_data data WHERE raw_contacts._id = data.raw_contact_id AND " +
        "data.mimetype = '" + CONTACT_MIMETYPE + "')";

    String fallbackSelection = ContactsContract.Data.SYNC2 + " IS NULL OR " + ContactsContract.Data.SYNC2 + " != '" + SYNC + "'";

    Cursor cursor;

    try {
      cursor = context.getContentResolver().query(uri, projection, excludeSelection, null, sort);
    } catch (Exception e) {
      Log.w(TAG, e);
      cursor = context.getContentResolver().query(uri, projection, fallbackSelection, null, sort);
    }

    return new ProjectionMappingCursor(cursor, projectionMap,
        new Pair<String, Object>(CONTACT_TYPE_COLUMN, NORMAL_TYPE));
  }

  private static class ProjectionMappingCursor extends CursorWrapper {

    private final Map<String, String>    projectionMap;
    private final Pair<String, Object>[] extras;

    @SafeVarargs
    public ProjectionMappingCursor(Cursor cursor,
                                   Map<String, String> projectionMap,
                                   Pair<String, Object>... extras)
    {
      super(cursor);
      this.projectionMap = projectionMap;
      this.extras        = extras;
    }

    @Override
    public int getColumnCount() {
      return super.getColumnCount() + extras.length;
    }

    @Override
    public int getColumnIndex(String columnName) {
      for (int i=0;i<extras.length;i++) {
        if (extras[i].first.equals(columnName)) {
          return super.getColumnCount() + i;
        }
      }

      return super.getColumnIndex(projectionMap.get(columnName));
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
      int index = getColumnIndex(columnName);

      if (index == -1) throw new IllegalArgumentException("Bad column name!");
      else             return index;
    }

    @Override
    public String getColumnName(int columnIndex) {
      int baseColumnCount = super.getColumnCount();

      if (columnIndex >= baseColumnCount) {
        int offset = columnIndex - baseColumnCount;
        return extras[offset].first;
      }

      return getReverseProjection(super.getColumnName(columnIndex));
    }

    @Override
    public String[] getColumnNames() {
      String[] names    = super.getColumnNames();
      String[] allNames = new String[names.length + extras.length];

      for (int i=0;i<names.length;i++) {
        allNames[i] = getReverseProjection(names[i]);
      }

      for (int i=0;i<extras.length;i++) {
        allNames[names.length + i] = extras[i].first;
      }

      return allNames;
    }

    @Override
    public int getInt(int columnIndex) {
      if (columnIndex >= super.getColumnCount()) {
        int offset = columnIndex - super.getColumnCount();
        return (Integer)extras[offset].second;
      }

      return super.getInt(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
      if (columnIndex >= super.getColumnCount()) {
        int offset = columnIndex - super.getColumnCount();
        return (String)extras[offset].second;
      }

      return super.getString(columnIndex);
    }

    private @Nullable
    String getReverseProjection(String columnName) {
      for (Map.Entry<String, String> entry : projectionMap.entrySet()) {
        if (entry.getValue().equals(columnName)) {
          return entry.getKey();
        }
      }

      return null;
    }
  }
}
