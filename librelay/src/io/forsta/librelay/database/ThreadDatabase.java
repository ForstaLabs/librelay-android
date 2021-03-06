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
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import io.forsta.librelay.atlas.model.RelayDistribution;
import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.R;
import io.forsta.librelay.color.MaterialColors;
import io.forsta.librelay.database.MessageDatabase.MarkedMessageInfo;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.media.Slide;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ThreadDatabase extends DbBase {

  private static final String TAG = ThreadDatabase.class.getSimpleName();

          static final String TABLE_NAME = "thread";
  public static final String ID = "_id";
  public static final String DATE = "date";
  public static final String TITLE = "title";
  public static final String UID = "uid";
  public static final String DISTRIBUTION = "distribution";
  public static final String PRETTY_EXPRESSION = "pretty_expression";
  public static final String MESSAGE_COUNT = "message_count";
  public static final String RECIPIENT_IDS = "recipient_ids";
  public static final String SNIPPET = "snippet";
  private static final String SNIPPET_CHARSET = "snippet_cs";
  public static final String READ = "read";
  public static final String TYPE = "type";
  private static final String ERROR = "error";
  public static final String SNIPPET_TYPE = "snippet_type";
  public static final String SNIPPET_URI = "snippet_uri";
  public static final String ARCHIVED = "archived";
  public static final String STATUS = "status";
  public static final String RECEIPT_COUNT = "delivery_receipt_count";
  public static final String EXPIRES_IN = "expires_in";
  public static final String PINNED = "pinned";
  public static final String THREAD_TYPE = "thread_type";
  public static final String THREAD_CREATOR = "thread_creator";
  public static final String SNIPPET_SENDER = "snippet_sender";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("                    +
    ID + " INTEGER PRIMARY KEY, " + DATE + " INTEGER DEFAULT 0, "                                  +
    MESSAGE_COUNT + " INTEGER DEFAULT 0, " + RECIPIENT_IDS + " TEXT, " + SNIPPET + " TEXT, "       +
    SNIPPET_CHARSET + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 1, "                       +
    TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, "                                 +
    SNIPPET_TYPE + " INTEGER DEFAULT 0, " + SNIPPET_URI + " TEXT DEFAULT NULL, "                   +
    ARCHIVED + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT 0, "                            +
    RECEIPT_COUNT + " INTEGER DEFAULT 0, " + EXPIRES_IN + " INTEGER DEFAULT 0, " +
      DISTRIBUTION + " TEXT, " +
      TITLE + " TEXT, " +
      UID + " TEXT, " + PRETTY_EXPRESSION + " TEXT, " + PINNED + " INTEGER DEFAULT 0, " + THREAD_TYPE + " INTEGER DEFAULT 0, " + THREAD_CREATOR + " TEXT, " + SNIPPET_SENDER + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + RECIPIENT_IDS + ");",
    "CREATE INDEX IF NOT EXISTS archived_index ON " + TABLE_NAME + " (" + ARCHIVED + ");",
  };

  public static final String PREFERENCES_JOIN = TABLE_NAME + " LEFT JOIN " + ThreadPreferenceDatabase.TABLE_NAME +
      " ON " + TABLE_NAME + "." + ID + " = " + ThreadPreferenceDatabase.TABLE_NAME + "." + ThreadPreferenceDatabase.THREAD_ID;

  public ThreadDatabase(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  private long createThreadForRecipients(String recipients, int recipientCount, int distributionType) {
    return createThreadForRecipients(recipients, recipientCount, distributionType, null);
  }

  private long createThreadForRecipients(String recipients, int recipientCount, int distributionType, String threadUid) {
    ContentValues contentValues = new ContentValues(5);
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(RECIPIENT_IDS, recipients);

    if (recipientCount > 1)
      contentValues.put(TYPE, distributionType);

    if (threadUid != null) {
      contentValues.put(UID, threadUid);
    } else {
      contentValues.put(UID, UUID.randomUUID().toString());
    }

    contentValues.put(MESSAGE_COUNT, 0);
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    return db.insert(TABLE_NAME, null, contentValues);
  }

  private void updateThread(long threadId, long count, String body, @Nullable Uri attachment,
                            long date, int status, int receiptCount, long type, boolean unarchive,
                            long expiresIn, String senderAddress)
  {
    ContentValues contentValues = new ContentValues(7);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(SNIPPET, body);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(STATUS, status);
    contentValues.put(RECEIPT_COUNT, receiptCount);
    contentValues.put(EXPIRES_IN, expiresIn);
    contentValues.put(SNIPPET_SENDER, senderAddress);

    if (unarchive) {
      contentValues.put(ARCHIVED, 0);
    }

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
    notifyConversationListeners(threadId);
  }

  public void updateDraftSnippet(long threadId, String snippet, @Nullable Uri attachment, long date, long type, boolean unarchive) {
    ContentValues contentValues = new ContentValues(4);

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(SNIPPET, snippet);
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_SENDER, TextSecurePreferences.getLocalAddress(context));

    if (unarchive) {
      contentValues.put(ARCHIVED, 0);
    }

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  private void deleteThread(long threadId) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.delete(TABLE_NAME, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  private void deleteThreads(Set<Long> threadIds) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    String where      = "";

    for (long threadId : threadIds) {
      where += ID + " = '" + threadId + "' OR ";
    }

    where = where.substring(0, where.length() - 4);

    db.delete(TABLE_NAME, where, null);
    notifyConversationListListeners();
  }

  private void deleteAllThreads() {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
    notifyConversationListListeners();
  }

  public void trimAllThreads(int length, ProgressListener listener) {
    Cursor cursor   = null;
    int threadCount = 0;
    int complete    = 0;

    try {
      cursor = this.getConversationList();

      if (cursor != null)
        threadCount = cursor.getCount();

      while (cursor != null && cursor.moveToNext()) {
        long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        trimThread(threadId, length);

        listener.onProgress(++complete, threadCount);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void trimThread(long threadId, int length) {
    Log.w("ThreadDatabase", "Trimming thread: " + threadId + " to: " + length);
    Cursor cursor = null;

    try {
      cursor = DbFactory.getMessageDatabase(context).getConversation(threadId);

      if (cursor != null && length > 0 && cursor.getCount() > length) {
        Log.w("ThreadDatabase", "Cursor count is greater than length!");
        cursor.moveToPosition(length - 1);

        long lastTweetDate = cursor.getLong(cursor.getColumnIndexOrThrow(MessageDatabase.DATE_RECEIVED));

        Log.w("ThreadDatabase", "Cut off tweet date: " + lastTweetDate);

        DbFactory.getMessageDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);

        update(threadId, false);
        notifyConversationListeners(threadId);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void setAllThreadsRead() {
    SQLiteDatabase db           = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);

    db.update(TABLE_NAME, contentValues, null, null);

    DbFactory.getMessageDatabase(context).setAllMessagesRead();
    notifyConversationListListeners();
  }

  public List<MarkedMessageInfo> setRead(long threadId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});

    final List<MarkedMessageInfo> messageRecords = DbFactory.getMessageDatabase(context).setMessagesRead(threadId);

    notifyConversationListListeners();

    return new LinkedList<MarkedMessageInfo>() {{
      addAll(messageRecords);
    }};
  }

  public void setUnread(long threadId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 0);

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void setDistributionType(long threadId, int distributionType) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(TYPE, distributionType);

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public Cursor getFilteredConversationList(String filter) {
    SQLiteDatabase   db                      = dbHelper.getReadableDatabase();
    List<Cursor>     cursors                 = new LinkedList<>();
    String titleSelection = TITLE + " LIKE ? ";
    String filterQuery = "%" + filter + "%";

    SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
    builder.setTables(PREFERENCES_JOIN);

    cursors.add(builder.query(db, null, titleSelection, new String[] {filterQuery}, null, null, PINNED + " DESC, " + DATE + " DESC"));

    Cursor cursor = cursors.size() > 1 ? new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) : cursors.get(0);
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getConversationList() {
    SQLiteDatabase db     = dbHelper.getReadableDatabase();
    SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
    builder.setTables(PREFERENCES_JOIN);
    Cursor cursor = builder.query(db, null, ARCHIVED + " = ?", new String[] {"0"}, null, null, PINNED + " DESC, " + DATE + " DESC");
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getConversationListWithoutAnnouncements() {
    SQLiteDatabase db     = dbHelper.getReadableDatabase();
    SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
    builder.setTables(PREFERENCES_JOIN);
    Cursor cursor = builder.query(db, null, ARCHIVED + " = ? AND " + THREAD_TYPE + " = ?", new String[] {"0", "0"}, null, null, PINNED + " DESC, " + DATE + " DESC");
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getArchivedConversationList() {
    SQLiteDatabase db     = dbHelper.getReadableDatabase();
    SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
    builder.setTables(PREFERENCES_JOIN);
    Cursor         cursor = builder.query(db, null, ARCHIVED + " = ?", new String[] {"1"}, null, null, DATE + " DESC");

    setNotifyConverationListListeners(cursor);

    return cursor;
  }

  public int getArchivedConversationListCount() {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, ARCHIVED + " = ?",
                        new String[] {"1"}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }

    } finally {
      if (cursor != null) cursor.close();
    }

    return 0;
  }

  public void archiveConversation(long threadId) {
    SQLiteDatabase db            = dbHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    contentValues.put(ARCHIVED, 1);

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void unarchiveConversation(long threadId) {
    SQLiteDatabase db            = dbHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    contentValues.put(ARCHIVED, 0);

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void deleteConversations(Set<Long> selectedConversations) {
    DbFactory.getMessageDatabase(context).deleteThreads(selectedConversations);
    DbFactory.getDraftDatabase(context).clearDrafts(selectedConversations);
    DbFactory.getThreadPreferenceDatabase(context).deleteThreadPreferences(selectedConversations);
    deleteThreads(selectedConversations);
    notifyConversationListeners(selectedConversations);
    notifyConversationListListeners();
  }

  public void deleteAllConversations() {
    DbFactory.getMessageDatabase(context).deleteAllThreads();
    DbFactory.getDraftDatabase(context).clearAllDrafts();
    DbFactory.getThreadPreferenceDatabase(context).deleteAllPreferences();
    deleteAllThreads();
  }

  public long getThreadIdFor(Recipients recipients, int distributionType) {
    String recipientsList  = recipients.getSortedAddresses();
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    String where           = RECIPIENT_IDS + " = ?";
    String[] recipientsArg = new String[] {recipientsList};
    Cursor cursor          = null;

    try {
      cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      else
        return createThreadForRecipients(recipientsList, recipientsList.length(), distributionType);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public ThreadRecord getThreadForDistribution(String distribution, int type) {
    SQLiteDatabase db      = dbHelper.getReadableDatabase();
    Cursor cursor          = null;
    try {
      SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
      builder.setTables(PREFERENCES_JOIN);
      cursor = builder.query(db, null, DISTRIBUTION + " = ? AND " + THREAD_TYPE + " = ? ", new String[]{distribution + "", type + ""}, null, null, null);

      Reader reader = readerFor(cursor);
      return reader.getNext();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public ThreadRecord allocateThread(Recipients recipients, RelayDistribution distribution, int threadType) {
    return allocateThread(recipients, distribution, threadType, null);
  }

  private ThreadRecord allocateThread(Recipients recipients, RelayDistribution distribution, int threadType, String threadUID) {
    String sortedAddresses = recipients.getSortedAddresses();
    ContentValues contentValues = new ContentValues();
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(RECIPIENT_IDS, sortedAddresses);
    contentValues.put(TYPE, DistributionTypes.DEFAULT);
    if (TextUtils.isEmpty(threadUID)) {
      contentValues.put(UID, UUID.randomUUID().toString());
    } else {
      contentValues.put(UID, threadUID);
    }
    contentValues.put(DISTRIBUTION, distribution.universal);
    contentValues.put(PRETTY_EXPRESSION, distribution.pretty);
    contentValues.put(MESSAGE_COUNT, 0);
    contentValues.put(THREAD_TYPE, threadType);
    contentValues.put(THREAD_CREATOR, TextSecurePreferences.getLocalAddress(context));

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    long threadId = db.insert(TABLE_NAME, null, contentValues);
    DbFactory.getThreadPreferenceDatabase(context).setColor(threadId, MaterialColors.getRandomConversationColor());
    return getThread(threadId);
  }

  public long allocateThreadId(RelayContent relayContent, RelayDistribution distribution) {
    Recipients recipients = RecipientFactory.getRecipientsFromStrings(context, distribution.getRecipients(context), false);
    String sortedAddresses  = recipients.getSortedAddresses();
    ContentValues contentValues = new ContentValues();
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(RECIPIENT_IDS, sortedAddresses);
    contentValues.put(TYPE, DistributionTypes.DEFAULT);
    contentValues.put(UID, relayContent.getThreadUId());
    contentValues.put(DISTRIBUTION, relayContent.getUniversalExpression());
    contentValues.put(PRETTY_EXPRESSION, distribution.pretty);
    contentValues.put(TITLE, relayContent.getThreadTitle());
    contentValues.put(MESSAGE_COUNT, 0);
    contentValues.put(THREAD_CREATOR, relayContent.getSenderId());
    contentValues.put(THREAD_TYPE, relayContent.getThreadType().equals(RelayContent.ThreadTypes.ANNOUNCEMENT) ? 1 : 0);

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    long threadId = db.insert(TABLE_NAME, null, contentValues);
    DbFactory.getThreadPreferenceDatabase(context).setColor(threadId, MaterialColors.getRandomConversationColor());
    RecipientFactory.clearCache(context);
    return threadId;
  }

  public long getOrAllocateThreadId(RelayContent relayContent, RelayDistribution distribution) {
    long threadId = getThreadIdForUid(relayContent.getThreadUId());
    if (threadId == -1) {
      threadId = allocateThreadId(relayContent, distribution);
    } else {
      updateThread(threadId, relayContent, distribution);
    }
    return threadId;
  }

  public long getThreadIdForUid(String threadUid) {
    SQLiteDatabase db      = dbHelper.getReadableDatabase();
    Cursor cursor          = null;
    try {
      cursor = db.query(TABLE_NAME, null, UID + " = ? ", new String[]{threadUid + ""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
    return -1;
  }

  public List<String> getAllRecipients() {
    Set<String> recipients = new HashSet<>();
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    Cursor cursor = null;
    try {
      cursor = db.query(TABLE_NAME, new String[] {RECIPIENT_IDS}, null, null, null, null, null);
      while (cursor != null && cursor.moveToNext()) {
        String ids = cursor.getString(cursor.getColumnIndex(RECIPIENT_IDS));
        String[] idsArray = ids.split(" ");
        for (int i=0; i<idsArray.length; i++) {
          if (!TextUtils.isEmpty(idsArray[i])) {
            recipients.add(idsArray[i]);
          }
        }
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

    return DbFactory.getContacts(context).getAddressesFromIds(new ArrayList<>(recipients));
  }

  public @Nullable Recipients getRecipientsForThreadId(long threadId) {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, null, ID + " = ?", new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        String recipientAddresses = cursor.getString(cursor.getColumnIndexOrThrow(RECIPIENT_IDS));
        return RecipientFactory.getRecipientsFromString(context, recipientAddresses, false);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  public void updatePinned(long threadId, boolean pinned) {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    ContentValues values = new ContentValues();
    values.put(PINNED, pinned);
    db.update(TABLE_NAME, values, ID_WHERE, new String[] {String.valueOf(threadId)});
    notifyConversationListListeners();
  }

  public void updateReadState(long threadId) {
    int unreadCount = DbFactory.getMessageDatabase(context).getUnreadCount(threadId);

    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, unreadCount == 0);

    dbHelper.getWritableDatabase().update(TABLE_NAME, contentValues,ID_WHERE,
                                                new String[] {String.valueOf(threadId)});

    notifyConversationListListeners();
  }

  public boolean update(long threadId, boolean unarchive) {
    MessageDatabase messageDatabase = DbFactory.getMessageDatabase(context);
    long count                    = messageDatabase.getConversationCount(threadId);

    if (count == 0) {
      deleteThread(threadId);
      notifyConversationListListeners();
      return true;
    }

    MessageDatabase.Reader reader = null;

    try {
      reader = messageDatabase.readerFor(messageDatabase.getConversationSnippet(threadId));
      MessageRecord record;

      if (reader != null && (record = reader.getNext()) != null) {
        updateThread(threadId, count, record.getBody(), getAttachmentUriFor(record),
                     record.getTimestamp(), record.getDeliveryStatus(), record.getReceiptCount(),
                     record.getType(), unarchive, record.getExpiresIn(), record.getIndividualRecipient().getAddress());
        notifyConversationListListeners();
        return false;
      } else {
        deleteThread(threadId);
        notifyConversationListListeners();
        return true;
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  public void updateThread(long threadId, RelayDistribution distribution) {
    updateThread(threadId, null, distribution);
  }

  public void updateThread(long threadId, @Nullable RelayContent message, RelayDistribution distribution) {
    Recipients distributionRecipients = RecipientFactory.getRecipientsFromStrings(context, distribution.getRecipients(context), false);
    ThreadRecord threadRecord = getThread(threadId);
    ContentValues values = new ContentValues();
    if (message != null && message.getThreadTitle() != null && !TextUtils.equals(threadRecord.getTitle(), message.getThreadTitle())) {
      values.put(TITLE, message.getThreadTitle());
    }
    if (!TextUtils.isEmpty(distribution.universal) && !distribution.universal.equals(threadRecord.getDistribution())) {
      Log.w(TAG, "Thread update, new distribution: " + distribution.universal);
      values.put(DISTRIBUTION, distribution.universal);
      values.put(PRETTY_EXPRESSION, distribution.pretty);
    }
    // Distribution may be the same, but tag members may have changed.
    if (!threadRecord.getRecipients().getSortedAddresses().equals(distributionRecipients.getSortedAddresses())) {
      Log.w(TAG, "New Recipients: " + TextUtils.join(", ", distribution.getRecipients(context)));
      values.put(RECIPIENT_IDS, distributionRecipients.getSortedAddresses());
    }
    if (values.size() > 0) {
      SQLiteDatabase db = dbHelper.getWritableDatabase();
      db.update(TABLE_NAME, values, ID + " = ?", new String[] {threadId + ""});
      notifyConversationListListeners();
      notifyConversationListeners(threadId);
    }
  }

  public ThreadRecord getThread(long threadId) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    Cursor cursor = null;
    try {
      SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
      builder.setTables(PREFERENCES_JOIN);
      cursor = builder.query(db, null, TABLE_NAME + "." + ID + " = ? ", new String[]{threadId + ""}, null, null, null);
      Reader reader = readerFor(cursor);
      return reader.getNext();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  public ThreadRecord getThread(String threadUid) {
      SQLiteDatabase db = dbHelper.getWritableDatabase();
      Cursor cursor = null;
      try {
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(PREFERENCES_JOIN);
        cursor = builder.query(db, null, UID + " = ? ", new String[]{threadUid}, null, null, null);
        Reader reader = readerFor(cursor);
        return reader.getNext();
      } finally {
        if (cursor != null)
          cursor.close();
      }
  }

  public void setThreadUnread(long threadId) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    ContentValues values = new ContentValues(1);
    values.put(READ, false);
    db.update(TABLE_NAME, values, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
    notifyConversationListeners(threadId);
  }

  public void updateThreadTitle(long threadId, String title) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    ContentValues values = new ContentValues(1);
    values.put(TITLE, title);

    db.update(TABLE_NAME, values, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListeners(threadId);
  }

  private @Nullable Uri getAttachmentUriFor(MessageRecord record) {
    SlideDeck slideDeck = record.getSlideDeck();
    Slide thumbnail = slideDeck.getThumbnailSlide();

    return thumbnail != null ? thumbnail.getThumbnailUri() : null;
  }

  public static interface ProgressListener {
    public void onProgress(int complete, int total);
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static class DistributionTypes {
    public static final int DEFAULT      = 2;
    public static final int BROADCAST    = 1;
    public static final int CONVERSATION = 2;
    public static final int ARCHIVE      = 3;
  }

  public static class ThreadTypes {
    public static final int CONVERSATION = 0;
    public static final int ANNOUNCEMENT = 1;
  }

  public class Reader {

    private final Cursor       cursor;

    public Reader(Cursor cursor) {
      this.cursor       = cursor;
    }

    public ThreadRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public ThreadRecord getCurrent() {
      long       threadId    = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
      String     recipientAddresses = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, recipientAddresses, false);
      String body = getPlaintextBody(cursor);
      long date               = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
      long count              = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
      long read               = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.READ));
      long type               = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
      int distributionType    = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
      boolean archived        = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0;
      int status              = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
      int receiptCount        = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.RECEIPT_COUNT));
      long expiresIn          = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
      String distribution = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.DISTRIBUTION));
      String title = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.TITLE));
      String threadUid = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.UID));
      Uri snippetUri          = getSnippetUri(cursor);
      String color = cursor.getString(cursor.getColumnIndexOrThrow(ThreadPreferenceDatabase.COLOR));
      String expression = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.PRETTY_EXPRESSION));
      boolean pinned = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.PINNED)) != 0;
      int threadType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.THREAD_TYPE));
      String sender = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_SENDER));
      String threadCreator = cursor.getString(cursor.getColumnIndex(ThreadDatabase.THREAD_CREATOR));

      return new ThreadRecord(context, body, snippetUri, recipients, date, count, read == 1,
                              threadId, receiptCount, status, type, distributionType, archived,
                              expiresIn, distribution, title, threadUid, color, expression, pinned, threadType, sender, threadCreator);
    }

    private String getPlaintextBody(Cursor cursor) {
      try {
        String body = cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET));
        return body != null ? body : "";
      } catch (Exception e) {
        Log.w("ThreadDatabase", e);
        return context.getString(R.string.ThreadDatabase_error_decrypting_message);
      }
    }

    private @Nullable Uri getSnippetUri(Cursor cursor) {
      if (cursor.isNull(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI))) {
        return null;
      }

      try {
        return Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI)));
      } catch (IllegalArgumentException e) {
        Log.w(TAG, e);
        return null;
      }
    }

    public void close() {
      cursor.close();
    }
  }
}
