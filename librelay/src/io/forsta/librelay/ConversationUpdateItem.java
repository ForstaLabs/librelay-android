package io.forsta.librelay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.Util;

import java.util.Locale;
import java.util.Set;

public class ConversationUpdateItem extends LinearLayout
    implements Recipients.RecipientsModifiedListener, Recipient.RecipientModifiedListener, BindableConversationItem
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private Set<MessageRecord> batchSelected;

  private ImageView     icon;
  private TextView      body;
  private TextView      date;
  private Recipient     sender;
  private MessageRecord messageRecord;
  private Locale        locale;

  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.icon = (ImageView)findViewById(R.id.conversation_update_icon);
    this.body = (TextView)findViewById(R.id.conversation_update_body);
    this.date = (TextView)findViewById(R.id.conversation_update_date);
  }

  @Override
  public void bind(@NonNull MessageRecord messageRecord,
                   @NonNull Locale locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   @NonNull Recipients conversationRecipients)
  {
    this.batchSelected = batchSelected;

    bind(messageRecord, locale);
  }

  @Override
  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  private void bind(@NonNull MessageRecord messageRecord, @NonNull Locale locale) {
    this.messageRecord = messageRecord;
    this.sender        = messageRecord.getIndividualRecipient();
    this.locale        = locale;

    this.sender.addListener(this);

    if (messageRecord.isExpirationTimerUpdate()) setTimerRecord(messageRecord);
    else if (messageRecord.isEndSession())            setEndSessionRecord(messageRecord);
    else                                              throw new AssertionError("Neither group nor log nor joined.");

    if (batchSelected.contains(messageRecord)) setSelected(true);
    else                                       setSelected(false);
  }

  private void setTimerRecord(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      icon.setImageResource(R.drawable.ic_timer_white_24dp);
      icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    } else {
      icon.setImageResource(R.drawable.ic_timer_off_white_24dp);
      icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    }

    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }

  private void setEndSessionRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_refresh_white_24dp);
    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }

  @Override
  public void onModified(Recipients recipients) {
    onModified(recipients.getPrimaryRecipient());
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        bind(messageRecord, locale);
      }
    });
  }

  @Override
  public void unbind() {
    if (sender != null) {
      sender.removeListener(this);
    }
  }
}
