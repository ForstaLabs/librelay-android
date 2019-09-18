package io.forsta.librelay;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.CursorAdapter;
import android.widget.TextView;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;

import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.util.LRUCache;
import io.forsta.librelay.util.TextSecurePreferences;

public class ReplyListAdapter extends CursorAdapter {
    private final static String TAG = ReplyListAdapter.class.getSimpleName();

    private Context      mContext;
    private int          mResource;
    private final @NonNull
    MessageDatabase db;

    private static final int MAX_CACHE_SIZE = 40;
    private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
            Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));


    public ReplyListAdapter(@NonNull Context context, int resource, Cursor cursor) {
        super(context, cursor);
        mContext = context;
        mResource = resource;
        db = DbFactory.getMessageDatabase(mContext);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(mResource, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        long messageId              = cursor.getLong(cursor.getColumnIndexOrThrow(MessageDatabase.ID));
        String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MessageDatabase.MESSAGE_BOX));

        MessageRecord messageRecord = getMessageRecord(messageId, cursor, type);
        Recipient author;
        if (messageRecord.isOutgoing()) {
            author = RecipientFactory.getRecipient(context, TextSecurePreferences.getLocalAddress(context), true);
        } else {
            author = messageRecord.getIndividualRecipient();
        }

        String body = messageRecord.getPlainTextBody();
        int vote = db.getVoteCount(messageRecord.getMessageId()); //messageRecord.getVoteCount();

        TextView voteCount = view.findViewById(R.id.reply_vote);
        AvatarImageView contactPhoto = view.findViewById(R.id.reply_contact_photo);
        TextView bodyText = view.findViewById(R.id.reply_text);

        if(vote > 0) {
            voteCount.setVisibility(View.VISIBLE);
            voteCount.setText("(" + String.valueOf(vote) + ")");
        } else {
            voteCount.setVisibility(View.GONE);
        }
        contactPhoto.setAvatar(author, true);
        bodyText.setText(body);
    }

    private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
//        final SoftReference<MessageRecord> reference = messageRecordCache.get(type + messageId);
//        if (reference != null) {
//            final MessageRecord record = reference.get();
//            if (record != null) return record;
//        }

        final MessageRecord messageRecord = db.readerFor(cursor).getCurrent();

        messageRecordCache.put(type + messageId, new SoftReference<>(messageRecord));

        return messageRecord;
    }
}
