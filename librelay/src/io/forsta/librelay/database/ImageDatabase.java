package io.forsta.librelay.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.attachments.AttachmentId;
import io.forsta.librelay.attachments.DatabaseAttachment;

public class ImageDatabase extends DbBase {

    private final static String IMAGES_QUERY = "SELECT " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL_ASPECT_RATIO + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MESSAGE_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", "
        + MessageDatabase.TABLE_NAME + "." + MessageDatabase.MESSAGE_BOX + ", "
        + MessageDatabase.TABLE_NAME + "." + MessageDatabase.DATE_SENT + ", "
        + MessageDatabase.TABLE_NAME + "." + MessageDatabase.DATE_RECEIVED + ", "
        + MessageDatabase.TABLE_NAME + "." + MessageDatabase.ADDRESS + " "
        + "FROM " + AttachmentDatabase.TABLE_NAME + " LEFT JOIN " + MessageDatabase.TABLE_NAME
        + " ON " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MESSAGE_ID + " = " + MessageDatabase.TABLE_NAME + "." + MessageDatabase.ID + " "
        + "WHERE " + AttachmentDatabase.MESSAGE_ID + " IN (SELECT " + MessageDatabase.ID
        + " FROM " + MessageDatabase.TABLE_NAME
        + " WHERE " + MessageDatabase.THREAD_ID + " = ?) AND "
        + AttachmentDatabase.CONTENT_TYPE + " LIKE 'image/%' AND "
        + AttachmentDatabase.DATA + " IS NOT NULL "
        + "ORDER BY " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " DESC";

  public ImageDatabase(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public Cursor getImagesForThread(long threadId) {
    SQLiteDatabase database = dbHelper.getReadableDatabase();
    Cursor cursor = database.rawQuery(IMAGES_QUERY, new String[]{threadId+""});
    setNotifyConverationListeners(cursor, threadId);
    return cursor;
  }

  public static class ImageRecord {
    private final AttachmentId attachmentId;
    private final long         mmsId;
    private final boolean      hasData;
    private final String       contentType;
    private final String       address;
    private final long         date;
    private final int          transferState;
    private final long         size;

    private ImageRecord(AttachmentId attachmentId, long mmsId, boolean hasData,
                        String contentType, String address, long date,
                        int transferState, long size)
    {
      this.attachmentId  = attachmentId;
      this.mmsId         = mmsId;
      this.hasData       = hasData;
      this.contentType   = contentType;
      this.address       = address;
      this.date          = date;
      this.transferState = transferState;
      this.size          = size;
    }

    public static ImageRecord from(Cursor cursor) {
      AttachmentId attachmentId = new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.ROW_ID)),
                                                   cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.UNIQUE_ID)));

      long date;

      if (MessageDatabase.Types.isPushType(cursor.getLong(cursor.getColumnIndexOrThrow(MessageDatabase.MESSAGE_BOX)))) {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MessageDatabase.DATE_SENT));
      } else {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MessageDatabase.DATE_RECEIVED));
      }

      return new ImageRecord(attachmentId,
                             cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.MESSAGE_ID)),
                             !cursor.isNull(cursor.getColumnIndexOrThrow(AttachmentDatabase.DATA)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AttachmentDatabase.CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MessageDatabase.ADDRESS)),
                             date,
                             cursor.getInt(cursor.getColumnIndexOrThrow(AttachmentDatabase.TRANSFER_STATE)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.SIZE)));
    }

    public Attachment getAttachment() {
      return new DatabaseAttachment(attachmentId, mmsId, hasData, contentType, transferState, size, null, null, null);
    }

    public String getContentType() {
      return contentType;
    }

    public String getAddress() {
      return address;
    }

    public long getDate() {
      return date;
    }

  }


}
