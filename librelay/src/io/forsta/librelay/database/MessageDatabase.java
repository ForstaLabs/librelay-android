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
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.database.documents.Document;
import io.forsta.librelay.database.model.MessageReceipt;
import io.forsta.librelay.messaging.OutgoingEndSessionMediaMessage;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.R;
import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.database.documents.IdentityKeyMismatch;
import io.forsta.librelay.database.documents.IdentityKeyMismatchList;
import io.forsta.librelay.database.documents.NetworkFailure;
import io.forsta.librelay.database.documents.NetworkFailureList;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.jobmanager.JobManager;
import io.forsta.librelay.jobs.TrimThreadJob;
import io.forsta.librelay.messaging.IncomingMediaMessage;
import io.forsta.librelay.messaging.OutgoingExpirationUpdateMessage;
import io.forsta.librelay.messaging.OutgoingMediaMessage;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.JsonUtils;
import io.forsta.librelay.util.Util;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ws.com.google.android.mms.MmsException;

public class MessageDatabase extends DbBase {
  private static final String TAG = MessageDatabase.class.getSimpleName();

  public  static final String TABLE_NAME = "messages";
  public static final String ID = "_id";
  public static final String THREAD_ID = "thread_id";
  public static final String MESSAGE_ID = "message_id";
  public static final String READ = "read";
  public static final String BODY = "body";
  public static final String ADDRESS = "address";
  public static final String ADDRESS_DEVICE_ID = "address_device_id";
  public static final String RECEIPT_COUNT = "delivery_receipt_count";
  public static final String MISMATCHED_IDENTITIES = "mismatched_identities";
  public static final String EXPIRES_IN = "expires_in";
  public static final String EXPIRE_STARTED = "expire_started";
  public static final String DATE_SENT = "date";
  public static final String DATE_RECEIVED = "date_received";
  public static final String MESSAGE_BOX = "msg_box";
  public static final String MESSAGE_SIZE = "m_size";
  public static final String STATUS = "st";
  public static final String PART_COUNT = "part_count";
  public static final String NETWORK_FAILURE = "network_failures";
  public static final String MESSAGE_REF = "message_ref";
  public static final String UP_VOTE = "vote";


  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
          THREAD_ID + " INTEGER, " +
          MESSAGE_ID + " TEXT, " +
          DATE_SENT + " INTEGER, " +
          DATE_RECEIVED + " INTEGER, " +
          MESSAGE_BOX + " INTEGER, " +
          READ + " INTEGER DEFAULT 0, " +
          BODY + " TEXT, " + PART_COUNT + " INTEGER, " +
          ADDRESS + " TEXT, " +
          ADDRESS_DEVICE_ID + " INTEGER, " +
          MESSAGE_SIZE + " INTEGER, " +
          STATUS + " INTEGER, " +
          RECEIPT_COUNT + " INTEGER DEFAULT 0, " +
          MISMATCHED_IDENTITIES + " TEXT DEFAULT NULL, "     +
          NETWORK_FAILURE + " TEXT DEFAULT NULL," +
          EXPIRES_IN + " INTEGER DEFAULT 0, " +
          EXPIRE_STARTED + " INTEGER DEFAULT 0, " +
          MESSAGE_REF + " TEXT, " +
          UP_VOTE + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_read_index ON " + TABLE_NAME + " (" + READ + ");",
    "CREATE INDEX IF NOT EXISTS mms_read_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_message_box_index ON " + TABLE_NAME + " (" + MESSAGE_BOX + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
    "CREATE INDEX IF NOT EXISTS mms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");",
    "CREATE INDEX IF NOT EXISTS mms_message_ref_index ON " + TABLE_NAME + " (" + MESSAGE_REF + ");",
    "CREATE INDEX IF NOT EXISTS mms_message_uid_index ON " + TABLE_NAME + " (" + MESSAGE_ID + ");"
  };

  private static final String[] MESSAGE_PROJECTION = new String[] {
      MessageDatabase.TABLE_NAME + "." + ID + " AS " + ID,
      THREAD_ID, DATE_SENT,
      DATE_RECEIVED,
      MESSAGE_BOX, READ,
      MESSAGE_SIZE, STATUS,
      BODY, PART_COUNT, ADDRESS, ADDRESS_DEVICE_ID,
      RECEIPT_COUNT, MISMATCHED_IDENTITIES, NETWORK_FAILURE,
      EXPIRES_IN, EXPIRE_STARTED, MESSAGE_REF, UP_VOTE, MESSAGE_ID,
      AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " AS " + AttachmentDatabase.ATTACHMENT_ID_ALIAS,
      AttachmentDatabase.UNIQUE_ID,
      AttachmentDatabase.MESSAGE_ID,
      AttachmentDatabase.SIZE,
      AttachmentDatabase.DATA,
      AttachmentDatabase.CONTENT_TYPE,
      AttachmentDatabase.CONTENT_LOCATION,
      AttachmentDatabase.CONTENT_DISPOSITION,
      AttachmentDatabase.NAME,
      AttachmentDatabase.TRANSFER_STATE
  };

  private static final String RAW_ID_WHERE = TABLE_NAME + "._id = ?";

  private final EarlyReceiptCache earlyReceiptCache = new EarlyReceiptCache();
  private final JobManager jobManager;

  public MessageDatabase(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
    this.jobManager = ApplicationContext.getInstance(context).getJobManager();
  }

  public Cursor getConversation(long threadId, long limit) {
    String order     = DATE_RECEIVED + " DESC";
    String selection = THREAD_ID + " = ? AND " + MESSAGE_REF + " IS NULL";

    Cursor cursor = rawQuery(selection, new String[] {threadId + ""}, order, limit > 0 ? String.valueOf(limit) : null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversation(long threadId) {
    return getConversation(threadId, 0);
  }

  public Cursor getReplies(String messageId) {
    String order = DATE_SENT + " ASC";
    String selection = MessageDatabase.MESSAGE_REF + " = ?";
    Cursor cursor = rawQuery(selection, new String[] {messageId + ""}, order, null);
    return cursor;
  }

  public int getVoteCount(String replyId) {
    int count = 0;
    String selection = MessageDatabase.MESSAGE_REF + " = ?";
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor cursor = null;
    try {
      cursor = database.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + selection, new String[] {replyId});
      if (cursor != null && cursor.moveToNext()) {
        int vote = cursor.getInt(cursor.getColumnIndex(UP_VOTE));
        // Count any record that is related to the reply as an up vote
        // This fixes issues with missing vote field in json payload.
        if (vote == 0) {
          vote = 1;
        }
        count += vote;
      }
    } finally {
      if (cursor != null) cursor.close();
    }
    return count;
  }

  public Cursor getIdentityConflictMessagesForThread(long threadId) {
    String order           = DATE_RECEIVED + " ASC";
    String selection       = THREAD_ID + " = ? AND " + MISMATCHED_IDENTITIES + " ?";

    Cursor cursor = rawQuery(selection, new String[] {threadId + "", "IS NOT NULL"}, order, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversationSnippet(long threadId) {
    String order     = DATE_RECEIVED + " DESC";
    String selection = THREAD_ID + " = ?";

    return  rawQuery(selection, new String[] {threadId + ""}, order, "1");
  }

  public Cursor getUnread() {
    String order           = DATE_RECEIVED + " ASC";
    String selection       = READ + " = ?";

    return rawQuery(selection, new String[] {"0"}, order, null);
  }

  public int getUnreadCount(long threadId) {
    String selection = READ + " = ? AND " + THREAD_ID + " = ?";
    Cursor cursor    = rawQuery(selection, new String[] {"0", threadId + ""}, null, null);

    try {
      return cursor != null ? cursor.getCount() : 0;
    } finally {
      if (cursor != null) cursor.close();;
    }
  }

  public int getConversationCount(long threadId) {
    return getMessageCountForThread(threadId);
  }

  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getInt(0);
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return 0;
  }

  public int getMessageCount() {
    SQLiteDatabase db = dbHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, null, null, null, null, null);

      if (cursor != null && cursor.moveToFirst()) return cursor.getInt(0);
      else                                        return 0;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public Reader getMessages(int skip, int limit) {
    Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME, null);
    return new Reader(cursor);
  }

  public void addFailures(long messageId, List<NetworkFailure> failure) {
    try {
      addToDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void removeFailure(long messageId, NetworkFailure failure) {
    try {
      removeFromDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void incrementDeliveryReceiptCount(SyncMessageId syncMessageId) {
    MessageReceiptsDatabase addressDatabase = DbFactory.getMessageReceiptDatabase(context);
    SQLiteDatabase     database        = dbHelper.getWritableDatabase();
    Cursor             cursor          = null;
    boolean            found           = false;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, MESSAGE_BOX}, DATE_SENT + " = ?", new String[] {String.valueOf(syncMessageId.getTimetamp())}, null, null, null, null);

      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX)))) {
          List<String> addresses = addressDatabase.getAddressesListForId(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));
          long messageId = cursor.getLong(cursor.getColumnIndex(ID));
          addressDatabase.updateDelivered(messageId, syncMessageId.getAddress(), syncMessageId.getTimetamp());

          Log.d(TAG, "Delivery receipt id: " + messageId + " ts: " + syncMessageId.getTimetamp() + " address: " + syncMessageId.getAddress());
          Log.d(TAG, "Addresses for message: " + addresses);

          for (String storedAddress : addresses) {
            try {
              String ourAddress   = syncMessageId.getAddress();
              String theirAddress = storedAddress;

              if (ourAddress.equals(theirAddress)) {
                long id       = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));

                found = true;

                database.execSQL("UPDATE " + TABLE_NAME + " SET " +
                                 RECEIPT_COUNT + " = " + RECEIPT_COUNT + " + 1 WHERE " + ID + " = ?",
                                 new String[] {String.valueOf(id)});

                DbFactory.getThreadDatabase(context).update(threadId, false);
                notifyConversationListeners(threadId);
              }
            } catch (Exception e) {
              Log.w(TAG, e);
            }
          }
        }
      }

      if (!found) {
        try {
          earlyReceiptCache.increment(syncMessageId.getTimetamp(), syncMessageId.getAddress());
        } catch (Exception e) {
          Log.w(TAG, e);
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public long getThreadIdForMessage(long id) {
    String sql        = "SELECT " + THREAD_ID + " FROM " + TABLE_NAME + " WHERE " + ID + " = ?";
    String[] sqlArgs  = new String[] {id+""};
    SQLiteDatabase db = dbHelper.getReadableDatabase();

    Cursor cursor = null;

    try {
      cursor = db.rawQuery(sql, sqlArgs);
      if (cursor != null && cursor.moveToFirst())
        return cursor.getLong(0);
      else
        return -1;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments, String order, String limit) {
    order = TextUtils.isEmpty(order) ? DATE_RECEIVED + " DESC" : order;
    limit = TextUtils.isEmpty(limit) ? "" : " LIMIT " + limit;
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    return database.rawQuery("SELECT " + Util.join(MESSAGE_PROJECTION, ",") +
        " FROM " + MessageDatabase.TABLE_NAME +  " LEFT OUTER JOIN " + AttachmentDatabase.TABLE_NAME +
        " ON (" + MessageDatabase.TABLE_NAME + "." + ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MESSAGE_ID + ")" +
        " WHERE " + where + " ORDER BY " + order + limit, arguments);
  }

  private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments) {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    return database.rawQuery("SELECT " + Util.join(MESSAGE_PROJECTION, ",") +
                             " FROM " + MessageDatabase.TABLE_NAME +  " LEFT OUTER JOIN " + AttachmentDatabase.TABLE_NAME +
                             " ON (" + MessageDatabase.TABLE_NAME + "." + ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MESSAGE_ID + ")" +
                             " WHERE " + where, arguments);
  }

  public Cursor getMessage(long messageId) {
    Cursor cursor = rawQuery(RAW_ID_WHERE, new String[] {messageId + ""});
    setNotifyConverationListeners(cursor, getThreadIdForMessage(messageId));
    return cursor;
  }

  public Reader getExpireStartedMessages() {
    String where = EXPIRE_STARTED + " > 0";
    return readerFor(rawQuery(where, null));
  }

  private void updateMailboxBitmask(long id, long maskOff, long maskOn, Optional<Long> threadId) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME +
                   " SET " + MESSAGE_BOX + " = (" + MESSAGE_BOX + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                   " WHERE " + ID + " = ?", new String[] {id + ""});

    if (threadId.isPresent()) {
      DbFactory.getThreadDatabase(context).update(threadId.get(), false);
    }
  }

  public void markAsOutbox(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_OUTBOX_TYPE, Optional.of(threadId));
  }

  public void markAsSending(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENDING_TYPE, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  public void markAsSentFailed(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  public void markAsSent(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  public void markDownloadState(long messageId, long state) {
    SQLiteDatabase database     = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, state);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId + ""});
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsNoSession(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_NO_SESSION_BIT, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  public void markAsSecure(long messageId) {
    updateMailboxBitmask(messageId, 0, Types.SECURE_MESSAGE_BIT, Optional.<Long>absent());
  }

  public void markAsPush(long messageId) {
    updateMailboxBitmask(messageId, 0, Types.PUSH_MESSAGE_BIT, Optional.<Long>absent());
  }

  public void markAsDecryptFailed(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_FAILED_BIT, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  public void markAsDecryptDuplicate(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_DUPLICATE_BIT, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  public void markExpireStarted(long messageId) {
    markExpireStarted(messageId, System.currentTimeMillis());
  }

  public void markExpireStarted(long messageId, long startedTimestamp) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(EXPIRE_STARTED, startedTimestamp);

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});

    long threadId = getThreadIdForMessage(messageId);
    notifyConversationListeners(threadId);
  }

  public void markAsRead(long messageId) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});

    long threadId = getThreadIdForMessage(messageId);
    notifyConversationListeners(threadId);
  }

  public List<MarkedMessageInfo> setMessagesRead(long threadId) {
    SQLiteDatabase          database  = dbHelper.getWritableDatabase();
    String                  where     = THREAD_ID + " = ? AND " + READ + " = 0";
    String[]                selection = new String[]{String.valueOf(threadId)};
    List<MarkedMessageInfo> result    = new LinkedList<>();
    Cursor                  cursor    = null;

    database.beginTransaction();

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, ADDRESS, DATE_SENT, MESSAGE_BOX, EXPIRES_IN, EXPIRE_STARTED}, where, selection, null, null, null);

      while(cursor != null && cursor.moveToNext()) {
        SyncMessageId  syncMessageId  = new SyncMessageId(cursor.getString(1), cursor.getLong(2));
        ExpirationInfo expirationInfo = new ExpirationInfo(cursor.getLong(0), cursor.getLong(4), cursor.getLong(5));

        result.add(new MarkedMessageInfo(syncMessageId, expirationInfo));
      }

      ContentValues contentValues = new ContentValues();
      contentValues.put(READ, 1);

      database.update(TABLE_NAME, contentValues, where, selection);
      database.setTransactionSuccessful();
    } finally {
      if (cursor != null) cursor.close();
      database.endTransaction();
    }

    return result;
  }

  public List<Pair<Long, Long>> setTimestampRead(SyncMessageId messageId, long expireStarted) {
    MessageReceiptsDatabase addressDatabase = DbFactory.getMessageReceiptDatabase(context);
    SQLiteDatabase         database        = dbHelper.getWritableDatabase();
    List<Pair<Long, Long>> expiring        = new LinkedList<>();
    Cursor                 cursor          = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, MESSAGE_BOX, EXPIRES_IN}, DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())}, null, null, null, null);

      while (cursor.moveToNext()) {
        List<String> addresses = addressDatabase.getAddressesListForId(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));

        for (String storedAddress : addresses) {
          try {
            String ourAddress   = messageId.getAddress();
            String theirAddress = storedAddress;

            if (ourAddress.equals(theirAddress)) {
              long id        = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
              long threadId  = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
              long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));

              ContentValues values = new ContentValues();
              values.put(READ, 1);

              if (expiresIn > 0) {
                values.put(EXPIRE_STARTED, expireStarted);
                expiring.add(new Pair<>(id, expiresIn));
              }

              database.update(TABLE_NAME, values, ID_WHERE, new String[]{String.valueOf(id)});

              DbFactory.getThreadDatabase(context).updateReadState(threadId);
              notifyConversationListeners(threadId);
            }
          } catch (Exception e) {
            Log.w("MessageDatabase", e);
          }
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return expiring;
  }

  public void setAllMessagesRead() {
    SQLiteDatabase database     = dbHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);

    database.update(TABLE_NAME, contentValues, null, null);
  }

  public OutgoingMediaMessage getOutgoingMessage(long messageId)
      throws MmsException, NoSuchMessageException
  {
    MessageReceiptsDatabase addr               = DbFactory.getMessageReceiptDatabase(context);
    AttachmentDatabase attachmentDatabase = DbFactory.getAttachmentDatabase(context);
    Cursor             cursor             = null;

    try {
      cursor = rawQuery(RAW_ID_WHERE, new String[] {String.valueOf(messageId)});

      if (cursor != null && cursor.moveToNext()) {
        long             outboxType     = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX));
        String           messageText    = cursor.getString(cursor.getColumnIndexOrThrow(BODY));
        long             timestamp      = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_SENT));
        long             expiresIn      = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
        List<Attachment> attachments    = new LinkedList<Attachment>(attachmentDatabase.getAttachmentsForMessage(messageId));
        List<String> destinations = addr.getAddressesListForId(messageId);
        String           body           = messageText;
        String messageUId = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE_ID));

        String messageRef = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE_REF));
        int vote = cursor.getInt(cursor.getColumnIndexOrThrow(UP_VOTE));

        Recipients recipients = RecipientFactory.getRecipientsFromStrings(context, destinations, false);

        if (Types.isExpirationTimerUpdate(outboxType)) {
          return new OutgoingExpirationUpdateMessage(recipients, body, timestamp, expiresIn);
        }

        if ((outboxType & Types.END_SESSION_BIT) !=0) {
          return new OutgoingEndSessionMediaMessage(recipients, body, attachments, timestamp, expiresIn, messageUId, messageRef, vote);
        } else {
          return new OutgoingMediaMessage(recipients, body, attachments, timestamp, expiresIn, messageUId, messageRef, vote);
        }
      }

      throw new NoSuchMessageException("No record found for id: " + messageId);
    } catch (Exception e) {
      throw new MmsException(e);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private long insertInbox(@NonNull SignalServiceEnvelope envelope) {

    ContentValues values = new ContentValues();
//    values.put(TYPE, envelope.getType());
    values.put(ADDRESS, envelope.getSource());
    values.put(ADDRESS_DEVICE_ID, envelope.getSourceDevice());
//    values.put(CONTENT, envelope.hasContent() ? Base64.encodeBytes(envelope.getContent()) : "");
    values.put(DATE_SENT, envelope.getTimestamp());

    return dbHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
  }

  private Pair<Long, Long> insertMessageInbox(IncomingMediaMessage retrieved,
                                              long threadId, long mailbox)
      throws MmsException
  {

    ContentValues contentValues = new ContentValues();

    contentValues.put(DATE_SENT, retrieved.getSentTimeMillis());
    contentValues.put(ADDRESS, retrieved.getAddress());

    contentValues.put(MESSAGE_BOX, mailbox);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, generatePduCompatTimestamp());
    contentValues.put(PART_COUNT, retrieved.getAttachments().size());
    contentValues.put(EXPIRES_IN, retrieved.getExpiresIn());
    contentValues.put(READ, retrieved.isExpirationUpdate() ? 1 : 0);
    if (!TextUtils.isEmpty(retrieved.getMessageRef())) {
      contentValues.put(MESSAGE_REF, retrieved.getMessageRef());
      if (retrieved.getVoteCount() > 0) {
        contentValues.put(UP_VOTE, retrieved.getVoteCount());
      }
    }
    contentValues.put(MESSAGE_ID, retrieved.getMessageId());

    if (!contentValues.containsKey(DATE_SENT)) {
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));
    }

    List<String> fromAddresses = new ArrayList<>(); //noop
    long messageId = insertMediaMessage(fromAddresses,
                                        retrieved.getBody(), retrieved.getAttachments(),
                                        contentValues);

    if (!Types.isExpirationTimerUpdate(mailbox)) {
      DbFactory.getThreadDatabase(context).setUnread(threadId);
      DbFactory.getThreadDatabase(context).update(threadId, true);
    }

    notifyConversationListeners(threadId);
    jobManager.add(new TrimThreadJob(context, threadId));

    return new Pair<>(messageId, threadId);
  }

  public Pair<Long, Long> insertSecureDecryptedMessageInbox(IncomingMediaMessage retrieved,
                                                            long threadId)
      throws MmsException
  {
    long type = Types.BASE_INBOX_TYPE | Types.SECURE_MESSAGE_BIT;
    type |= Types.PUSH_MESSAGE_BIT;

    type |= Types.ENCRYPTION_SYMMETRIC_BIT;

    if (retrieved.isEndSession()) {
      type |= Types.END_SESSION_BIT;
    }

    if (retrieved.isExpirationUpdate()) {
      type |= Types.EXPIRATION_TIMER_UPDATE_BIT;
    }

    return insertMessageInbox(retrieved, threadId, type);
  }

  public long insertMessageOutbox(@NonNull OutgoingMediaMessage message,
                                  long threadId)
      throws MmsException
  {
    long type = Types.BASE_OUTBOX_TYPE;
    type |= Types.ENCRYPTION_SYMMETRIC_BIT;
    type |= Types.SECURE_MESSAGE_BIT;

    if (message.isEndSession()) type |= Types.END_SESSION_BIT;

    if (message.isExpirationUpdate()) {
      type |= Types.EXPIRATION_TIMER_UPDATE_BIT;
    }

    List<String> recipientNumbers = message.getRecipients().toNumberStringList(true);

    ContentValues contentValues = new ContentValues();
    contentValues.put(DATE_SENT, message.getSentTimeMillis());
    contentValues.put(MESSAGE_BOX, type);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(READ, 1);
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(EXPIRES_IN, message.getExpiresIn());
    if (!TextUtils.isEmpty(message.getMessageRef())) {
      contentValues.put(MESSAGE_REF, message.getMessageRef());
      if (message.getVoteCount() > 0) {
        contentValues.put(UP_VOTE, message.getVoteCount());
      }
    }
    contentValues.put(MESSAGE_ID, message.getMessageId());

    Recipients recipients = message.getRecipients();
    boolean isSelfSend = recipients.isSingleRecipient() && recipients.includesSelf(context);
    if (isSelfSend) {
      contentValues.put(RECEIPT_COUNT, 1);
    } else {
      for (Recipient recipient : message.getRecipients()) {
        long receiptCount = earlyReceiptCache.remove(message.getSentTimeMillis(), recipient.getAddress());
        contentValues.put(RECEIPT_COUNT, receiptCount);
      }
    }
    contentValues.remove(ADDRESS);

    long messageId = insertMediaMessage(recipientNumbers, message.getBody(),
                                        message.getAttachments(), contentValues);

    jobManager.add(new TrimThreadJob(context, threadId));

    return messageId;
  }

  private long insertMediaMessage(@NonNull List<String> addresses,
                                  @Nullable String body,
                                  @NonNull List<Attachment> attachments,
                                  @NonNull ContentValues contentValues)
      throws MmsException
  {
    SQLiteDatabase     db              = dbHelper.getWritableDatabase();
    AttachmentDatabase partsDatabase   = DbFactory.getAttachmentDatabase(context);
    MessageReceiptsDatabase messageAddressDb = DbFactory.getMessageReceiptDatabase(context);

    if (!TextUtils.isEmpty(body)) {
      contentValues.put(BODY, body);
    }

    contentValues.put(PART_COUNT, attachments.size());

    db.beginTransaction();
    try {
      long messageId = db.insert(TABLE_NAME, null, contentValues);
      messageAddressDb.insertAddressesForId(messageId, addresses);
      partsDatabase.insertAttachmentsForMessage(messageId, attachments);

      db.setTransactionSuccessful();
      return messageId;
    } finally {
      db.endTransaction();

      notifyConversationListeners(contentValues.getAsLong(THREAD_ID));
      DbFactory.getThreadDatabase(context).update(contentValues.getAsLong(THREAD_ID), true);
    }
  }

  public boolean delete(long messageId) {
    long               threadId           = getThreadIdForMessage(messageId);
    MessageReceiptsDatabase addrDatabase       = DbFactory.getMessageReceiptDatabase(context);
    AttachmentDatabase attachmentDatabase = DbFactory.getAttachmentDatabase(context);
    attachmentDatabase.deleteAttachmentsForMessage(messageId);
    addrDatabase.deleteAddressesForId(messageId);

    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});
    boolean threadDeleted = DbFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
    return threadDeleted;
  }

  public void deleteThread(long threadId) {
    Set<Long> singleThreadSet = new HashSet<>();
    singleThreadSet.add(threadId);
    deleteThreads(singleThreadSet);
  }

  /*package*/ void deleteThreads(Set<Long> threadIds) {
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    String where      = "";
    Cursor cursor     = null;

    // TODO Fix this query. Either wrap in transaction, or convert to THREAD_ID + ' in (1,2,3)'
    for (long threadId : threadIds) {
      where += THREAD_ID + " = '" + threadId + "' OR ";
    }

    where = where.substring(0, where.length() - 4);

    try {
      cursor = db.query(TABLE_NAME, new String[] {ID}, where, null, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        delete(cursor.getLong(0));
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  /*package*/void deleteMessagesInThreadBeforeDate(long threadId, long date) {
    Cursor cursor = null;

    try {
      SQLiteDatabase db = dbHelper.getReadableDatabase();
      String where      = THREAD_ID + " = ? AND (CASE (" + MESSAGE_BOX + " & " + Types.BASE_TYPE_MASK + ") ";

      for (long outgoingType : Types.OUTGOING_MESSAGE_TYPES) {
        where += " WHEN " + outgoingType + " THEN " + DATE_SENT + " < " + date;
      }

      where += (" ELSE " + DATE_RECEIVED + " < " + date + " END)");

      Log.w("MessageDatabase", "Executing trim query: " + where);
      cursor = db.query(TABLE_NAME, new String[] {ID}, where, new String[] {threadId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        Log.w("MessageDatabase", "Trimming: " + cursor.getLong(0));
        delete(cursor.getLong(0));
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void deleteAllThreads() {
    DbFactory.getAttachmentDatabase(context).deleteAllAttachments();
    DbFactory.getMessageReceiptDatabase(context).deleteAllAddresses();

    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  public Reader readerFor(@NonNull Cursor cursor) {
    return new Reader(cursor);
  }

  public static class Status {
    public static final int DOWNLOAD_INITIALIZED     = 1;
    public static final int DOWNLOAD_NO_CONNECTIVITY = 2;
    public static final int DOWNLOAD_CONNECTING      = 3;
    public static final int DOWNLOAD_SOFT_FAILURE    = 4;
    public static final int DOWNLOAD_HARD_FAILURE    = 5;
    public static final int DOWNLOAD_APN_UNAVAILABLE = 6;

    public static boolean isDisplayDownloadButton(int status) {
      return
          status == DOWNLOAD_INITIALIZED     ||
          status == DOWNLOAD_NO_CONNECTIVITY ||
          status == DOWNLOAD_SOFT_FAILURE;
    }

    public static String getLabelForStatus(Context context, int status) {
      switch (status) {
        case DOWNLOAD_CONNECTING:      return context.getString(R.string.MmsDatabase_connecting_to_mms_server);
        case DOWNLOAD_INITIALIZED:     return context.getString(R.string.MmsDatabase_downloading_mms);
        case DOWNLOAD_HARD_FAILURE:    return context.getString(R.string.MmsDatabase_mms_download_failed);
        case DOWNLOAD_APN_UNAVAILABLE: return context.getString(R.string.MmsDatabase_mms_pending_download);
      }

      return context.getString(R.string.MmsDatabase_downloading);
    }

    public static boolean isHardError(int status) {
      return status == DOWNLOAD_HARD_FAILURE;
    }
  }

  public class Reader {

    private final Cursor       cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public MessageRecord getCurrent() {
      return getMessageRecord(cursor);
    }

    public int getCount() {
      if (cursor == null) return 0;
      else                return cursor.getCount();
    }

    private MessageRecord getMessageRecord(Cursor cursor) {
      long id                 = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      long dateSent           = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_SENT));
      long dateReceived       = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_RECEIVED));
      long box                = cursor.getLong(cursor.getColumnIndexOrThrow(MessageDatabase.MESSAGE_BOX));
      long threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
      String address          = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
      int addressDeviceId     = cursor.getInt(cursor.getColumnIndexOrThrow(ADDRESS_DEVICE_ID));
      int receiptCount        = cursor.getInt(cursor.getColumnIndexOrThrow(RECEIPT_COUNT));
      String body = getBody(cursor);
      int partCount           = cursor.getInt(cursor.getColumnIndexOrThrow(MessageDatabase.PART_COUNT));
      String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(MISMATCHED_IDENTITIES));
      String networkDocument  = cursor.getString(cursor.getColumnIndexOrThrow(MessageDatabase.NETWORK_FAILURE));
      long expiresIn          = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
      long expireStarted      = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRE_STARTED));
      String messageRef = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE_REF));
      int voteCount = cursor.getInt(cursor.getColumnIndexOrThrow(UP_VOTE));
      String messageId = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE_ID));
      List<MessageReceipt> receipts = new LinkedList<>();
      if (address == null) {
        // Outgoing message
        address = TextSecurePreferences.getLocalAddress(context);
        receipts = DbFactory.getMessageReceiptDatabase(context).getReceiptsForMessageId(id);
      }
      Recipients sender = getRecipientsFor(address);
      List<IdentityKeyMismatch> mismatches = getMismatchedIdentities(mismatchDocument);
      List<NetworkFailure> networkFailures = getFailures(networkDocument);
      SlideDeck slideDeck = getSlideDeck(cursor);

      return new MessageRecord(context, id, sender, sender.getPrimaryRecipient(),
                                       addressDeviceId, dateSent, dateReceived, receiptCount,
                                       threadId, body, slideDeck, partCount, box, mismatches,
                                       networkFailures, expiresIn, expireStarted, messageRef, voteCount, messageId, receipts);
    }

    private Recipients getRecipientsFor(@NonNull String address) {
      return RecipientFactory.getRecipientsFromString(context, address, true);
    }

    private List<IdentityKeyMismatch> getMismatchedIdentities(String document) {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, IdentityKeyMismatchList.class).getList();
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      return new LinkedList<>();
    }

    private List<NetworkFailure> getFailures(String document) {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, NetworkFailureList.class).getList();
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
        }
      }
      return new LinkedList<>();
    }

    private String getBody(Cursor cursor) {
      try {
        String body = cursor.getString(cursor.getColumnIndexOrThrow(BODY));
        return body != null ? body : "";
      } catch (Exception e) {
        Log.w("MessageDatabase", e);
        return context.getString(R.string.MmsDatabase_error_decrypting_message);
      }
    }

    private SlideDeck getSlideDeck(@NonNull Cursor cursor) {
      Attachment attachment = DbFactory.getAttachmentDatabase(context).getAttachment(cursor);
      return new SlideDeck(context, attachment);
    }

    public void close() {
      cursor.close();
    }
  }

  private long generatePduCompatTimestamp() {
    final long time = System.currentTimeMillis();
    return time - (time % 1000);
  }

  public void setMismatchedIdentity(long messageId, final long recipientId, final IdentityKey identityKey) {
    List<IdentityKeyMismatch> items = new ArrayList<IdentityKeyMismatch>() {{
      add(new IdentityKeyMismatch(recipientId, identityKey));
    }};

    IdentityKeyMismatchList document = new IdentityKeyMismatchList(items);

    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      setDocument(database, messageId, MISMATCHED_IDENTITIES, document);

      database.setTransactionSuccessful();
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    } finally {
      database.endTransaction();
    }
  }

  public void addMismatchedIdentity(long messageId, long recipientId, IdentityKey identityKey) {
    try {
      addToDocument(messageId, MISMATCHED_IDENTITIES,
          new IdentityKeyMismatch(recipientId, identityKey),
          IdentityKeyMismatchList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void removeMismatchedIdentity(long messageId, long recipientId, IdentityKey identityKey) {
    try {
      removeFromDocument(messageId, MISMATCHED_IDENTITIES,
          new IdentityKeyMismatch(recipientId, identityKey),
          IdentityKeyMismatchList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  protected <D extends Document<I>, I> void removeFromDocument(long messageId, String column, I object, Class<D> clazz) throws IOException {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      D           document = getDocument(database, messageId, column, clazz);
      Iterator<I> iterator = document.getList().iterator();

      while (iterator.hasNext()) {
        I item = iterator.next();

        if (item.equals(object)) {
          iterator.remove();
          break;
        }
      }

      setDocument(database, messageId, column, document);
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  protected <T extends Document<I>, I> void addToDocument(long messageId, String column, final I object, Class<T> clazz) throws IOException {
    List<I> list = new ArrayList<I>() {{
      add(object);
    }};

    addToDocument(messageId, column, list, clazz);
  }

  protected <T extends Document<I>, I> void addToDocument(long messageId, String column, List<I> objects, Class<T> clazz) throws IOException {
    SQLiteDatabase database = dbHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      T document = getDocument(database, messageId, column, clazz);
      document.getList().addAll(objects);
      setDocument(database, messageId, column, document);

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  private void setDocument(SQLiteDatabase database, long messageId, String column, Document document) throws IOException {
    ContentValues contentValues = new ContentValues();

    if (document == null || document.size() == 0) {
      contentValues.put(column, (String)null);
    } else {
      contentValues.put(column, JsonUtils.toJson(document));
    }

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }

  private <D extends Document> D getDocument(SQLiteDatabase database, long messageId,
                                             String column, Class<D> clazz)
  {
    Cursor cursor = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {column},
          ID_WHERE, new String[] {String.valueOf(messageId)},
          null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        String document = cursor.getString(cursor.getColumnIndexOrThrow(column));

        try {
          if (!TextUtils.isEmpty(document)) {
            return JsonUtils.fromJson(document, clazz);
          }
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      try {
        return clazz.newInstance();
      } catch (InstantiationException e) {
        throw new AssertionError(e);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public static class SyncMessageId {

    private final String address;
    private final long   timetamp;

    public SyncMessageId(String address, long timetamp) {
      this.address  = address;
      this.timetamp = timetamp;
    }

    public String getAddress() {
      return address;
    }

    public long getTimetamp() {
      return timetamp;
    }
  }

  public static class ExpirationInfo {

    private final long    id;
    private final long    expiresIn;
    private final long    expireStarted;

    public ExpirationInfo(long id, long expiresIn, long expireStarted) {
      this.id            = id;
      this.expiresIn     = expiresIn;
      this.expireStarted = expireStarted;
    }

    public long getId() {
      return id;
    }

    public long getExpiresIn() {
      return expiresIn;
    }

    public long getExpireStarted() {
      return expireStarted;
    }

    public boolean isMms() {
      return true;
    }
  }

  public static class MarkedMessageInfo {

    private final SyncMessageId  syncMessageId;
    private final ExpirationInfo expirationInfo;

    public MarkedMessageInfo(SyncMessageId syncMessageId, ExpirationInfo expirationInfo) {
      this.syncMessageId  = syncMessageId;
      this.expirationInfo = expirationInfo;
    }

    public SyncMessageId getSyncMessageId() {
      return syncMessageId;
    }

    public ExpirationInfo getExpirationInfo() {
      return expirationInfo;
    }
  }

  public static class Types {
    protected static final long TOTAL_MASK = 0xFFFFFFFF;

    // Base Types
    protected static final long BASE_TYPE_MASK                     = 0x1F;

    public static final long BASE_INBOX_TYPE                    = 20; //0x14
    protected static final long BASE_OUTBOX_TYPE                   = 21;
    protected static final long BASE_SENDING_TYPE                  = 22;
    public static final long BASE_SENT_TYPE                     = 23; //0x17
    public static final long BASE_SENT_FAILED_TYPE              = 24;
    protected static final long BASE_PENDING_SECURE_SMS_FALLBACK   = 25;
    protected static final long BASE_PENDING_INSECURE_SMS_FALLBACK = 26;
    public    static final long BASE_DRAFT_TYPE                    = 27;

    protected static final long[] OUTGOING_MESSAGE_TYPES = {BASE_OUTBOX_TYPE, BASE_SENT_TYPE,
        BASE_SENDING_TYPE, BASE_SENT_FAILED_TYPE,
        BASE_PENDING_SECURE_SMS_FALLBACK,
        BASE_PENDING_INSECURE_SMS_FALLBACK};

    // Message attributes
    private static final long MESSAGE_ATTRIBUTE_MASK = 0xE0;
    private static final long MESSAGE_FORCE_SMS_BIT  = 0x40;

    // Key Exchange Information
    protected static final long KEY_EXCHANGE_MASK                = 0xFF00;
    protected static final long KEY_EXCHANGE_BIT                 = 0x8000;
    protected static final long KEY_EXCHANGE_STALE_BIT           = 0x4000;
    protected static final long KEY_EXCHANGE_PROCESSED_BIT       = 0x2000;
    protected static final long KEY_EXCHANGE_CORRUPTED_BIT       = 0x1000;
    protected static final long KEY_EXCHANGE_INVALID_VERSION_BIT =  0x800;
    protected static final long KEY_EXCHANGE_BUNDLE_BIT          =  0x400;
    protected static final long KEY_EXCHANGE_IDENTITY_UPDATE_BIT =  0x200;

    // Secure Message Information
    protected static final long SECURE_MESSAGE_BIT = 0x800000;
    protected static final long END_SESSION_BIT    = 0x400000;
    protected static final long PUSH_MESSAGE_BIT   = 0x200000;

    protected static final long EXPIRATION_TIMER_UPDATE_BIT = 0x40000;

    // Encrypted Storage Information
    protected static final long ENCRYPTION_MASK                  = 0xFF000000;
    public static final long ENCRYPTION_SYMMETRIC_BIT         = 0x80000000;
    protected static final long ENCRYPTION_ASYMMETRIC_BIT        = 0x40000000;
    protected static final long ENCRYPTION_REMOTE_BIT            = 0x20000000;
    protected static final long ENCRYPTION_REMOTE_FAILED_BIT     = 0x10000000;
    protected static final long ENCRYPTION_REMOTE_NO_SESSION_BIT = 0x08000000;
    protected static final long ENCRYPTION_REMOTE_DUPLICATE_BIT  = 0x04000000;
    protected static final long ENCRYPTION_REMOTE_LEGACY_BIT     = 0x02000000;

    public static boolean isDraftMessageType(long type) {
      return (type & BASE_TYPE_MASK) == BASE_DRAFT_TYPE;
    }

    public static boolean isFailedMessageType(long type) {
      return (type & BASE_TYPE_MASK) == BASE_SENT_FAILED_TYPE;
    }

    public static boolean isOutgoingMessageType(long type) {
      for (long outgoingType : OUTGOING_MESSAGE_TYPES) {
        if ((type & BASE_TYPE_MASK) == outgoingType)
          return true;
      }

      return false;
    }

    public static boolean isPendingMessageType(long type) {
      return
          (type & BASE_TYPE_MASK) == BASE_OUTBOX_TYPE ||
              (type & BASE_TYPE_MASK) == BASE_SENDING_TYPE;
    }

    public static boolean isInboxType(long type) {
      return (type & BASE_TYPE_MASK) == BASE_INBOX_TYPE;
    }

    public static boolean isSecureType(long type) {
      return (type & SECURE_MESSAGE_BIT) != 0;
    }

    public static boolean isPushType(long type) {
      return (type & PUSH_MESSAGE_BIT) != 0;
    }

    public static boolean isEndSessionType(long type) {
      return (type & END_SESSION_BIT) != 0;
    }

    public static boolean isKeyExchangeType(long type) {
      return (type & KEY_EXCHANGE_BIT) != 0;
    }

    public static boolean isStaleKeyExchange(long type) {
      return (type & KEY_EXCHANGE_STALE_BIT) != 0;
    }

    public static boolean isProcessedKeyExchange(long type) {
      return (type & KEY_EXCHANGE_PROCESSED_BIT) != 0;
    }

    public static boolean isCorruptedKeyExchange(long type) {
      return (type & KEY_EXCHANGE_CORRUPTED_BIT) != 0;
    }

    public static boolean isInvalidVersionKeyExchange(long type) {
      return (type & KEY_EXCHANGE_INVALID_VERSION_BIT) != 0;
    }

    public static boolean isBundleKeyExchange(long type) {
      return (type & KEY_EXCHANGE_BUNDLE_BIT) != 0;
    }

    public static boolean isIdentityUpdate(long type) {
      return (type & KEY_EXCHANGE_IDENTITY_UPDATE_BIT) != 0;
    }

    public static boolean isExpirationTimerUpdate(long type) {
      return (type & EXPIRATION_TIMER_UPDATE_BIT) != 0;
    }

    public static boolean isFailedDecryptType(long type) {
      return (type & ENCRYPTION_REMOTE_FAILED_BIT) != 0;
    }

    public static boolean isDuplicateMessageType(long type) {
      return (type & ENCRYPTION_REMOTE_DUPLICATE_BIT) != 0;
    }

    public static boolean isDecryptInProgressType(long type) {
      return (type & ENCRYPTION_ASYMMETRIC_BIT) != 0;
    }

    public static boolean isNoRemoteSessionType(long type) {
      return (type & ENCRYPTION_REMOTE_NO_SESSION_BIT) != 0;
    }

    public static long translateFromSystemBaseType(long theirType) {
//    public static final int NONE_TYPE           = 0;
//    public static final int INBOX_TYPE          = 1;
//    public static final int SENT_TYPE           = 2;
//    public static final int SENT_PENDING        = 4;
//    public static final int FAILED_TYPE         = 5;

      switch ((int)theirType) {
        case 1: return BASE_INBOX_TYPE;
        case 2: return BASE_SENT_TYPE;
        case 3: return BASE_DRAFT_TYPE;
        case 4: return BASE_OUTBOX_TYPE;
        case 5: return BASE_SENT_FAILED_TYPE;
        case 6: return BASE_OUTBOX_TYPE;
      }

      return BASE_INBOX_TYPE;
    }

    public static int translateToSystemBaseType(long type) {
      if      (isInboxType(type))           return 1;
      else if (isOutgoingMessageType(type)) return 2;
      else if (isFailedMessageType(type))   return 5;

      return 1;
    }


//
//
//
//    public static final int NONE_TYPE           = 0;
//    public static final int INBOX_TYPE          = 1;
//    public static final int SENT_TYPE           = 2;
//    public static final int SENT_PENDING        = 4;
//    public static final int FAILED_TYPE         = 5;
//
//    public static final int OUTBOX_TYPE = 43;  // Messages are stored local encrypted and need delivery.
//
//
//    public static final int ENCRYPTING_TYPE      = 42;  // Messages are stored local encrypted and need async encryption and delivery.
//    public static final int SECURE_SENT_TYPE     = 44;  // Messages were sent with async encryption.
//    public static final int SECURE_RECEIVED_TYPE = 45;  // Messages were received with async decryption.
//    public static final int FAILED_DECRYPT_TYPE  = 46;  // Messages were received with async encryption and failed to decrypt.
//    public static final int DECRYPTING_TYPE      = 47;  // Messages are in the process of being asymmetricaly decrypted.
//    public static final int NO_SESSION_TYPE      = 48;  // Messages were received with async encryption but there is no session yet.
//
//    public static final int OUTGOING_KEY_EXCHANGE_TYPE  = 49;
//    public static final int INCOMING_KEY_EXCHANGE_TYPE  = 50;
//    public static final int STALE_KEY_EXCHANGE_TYPE     = 51;
//    public static final int PROCESSED_KEY_EXCHANGE_TYPE = 52;
//
//    public static final int[] OUTGOING_MESSAGE_TYPES = {SENT_TYPE, SENT_PENDING, ENCRYPTING_TYPE,
//                                                        OUTBOX_TYPE, SECURE_SENT_TYPE,
//                                                        FAILED_TYPE, OUTGOING_KEY_EXCHANGE_TYPE};
//
//    public static boolean isFailedMessageType(long type) {
//      return type == FAILED_TYPE;
//    }
//
//    public static boolean isOutgoingMessageType(long type) {
//      for (int outgoingType : OUTGOING_MESSAGE_TYPES) {
//        if (type == outgoingType)
//          return true;
//      }
//
//      return false;
//    }
//
//    public static boolean isPendingMessageType(long type) {
//      return type == SENT_PENDING || type == ENCRYPTING_TYPE || type == OUTBOX_TYPE;
//    }
//
//    public static boolean isSecureType(long type) {
//      return
//          type == SECURE_SENT_TYPE     || type == ENCRYPTING_TYPE ||
//          type == SECURE_RECEIVED_TYPE || type == DECRYPTING_TYPE;
//    }
//
//    public static boolean isKeyExchangeType(long type) {
//      return type == OUTGOING_KEY_EXCHANGE_TYPE || type == INCOMING_KEY_EXCHANGE_TYPE;
//    }
  }
}
