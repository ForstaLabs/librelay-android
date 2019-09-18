package io.forsta.librelay.components;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.forsta.librelay.R;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.ViewUtil;

public class ConversationTitleView extends LinearLayout {

  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private TextView  title;
  private TextView  subtitle;
  private ImageView announcement;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);

  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.title    = (TextView) findViewById(R.id.title);
    this.subtitle = (TextView) findViewById(R.id.subtitle);
    this.announcement = (ImageView) findViewById(R.id.conversation_title_announcement_indicator);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void setTitle(ThreadRecord thread) {
    Recipients recipients = thread.getRecipients();
    if (recipients == null) {
      setComposeTitle();
    } else {
      setRecipientsTitle(thread);
    }
    title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    this.subtitle.setVisibility(View.GONE);
  }

  private void setRecipientsTitle(ThreadRecord thread) {
    Recipients recipients = thread.getRecipients();
    int size = recipients.getRecipientsList().size();
    Recipient recipient = recipients.getPrimaryRecipient();

    if (thread.isAnnouncement()) {
      title.setText(getContext().getString(R.string.ConversationActivity_announcement));
      this.subtitle.setText(recipient.getName());
      subtitle.setVisibility(View.VISIBLE);
      announcement.setVisibility(VISIBLE);
    } else {
      announcement.setVisibility(GONE);
      if (recipients.isSingleRecipient()) {
        this.title.setText(recipient.getName());
        this.subtitle.setText(recipient.getFullTag());
        this.subtitle.setVisibility(View.VISIBLE);
      } else {
        title.setText(getContext().getString(R.string.ConversationActivity_group_conversation));
        subtitle.setText(getContext().getResources().getQuantityString(R.plurals.ConversationActivity_d_recipients_in_group, size, size));
        subtitle.setVisibility(View.VISIBLE);
        if (!recipients.includesSelf(getContext())) {
          subtitle.setText("You left this conversation.");
        }
      }
    }

    if (!TextUtils.isEmpty(thread.getTitle())) {
      title.setText(thread.getTitle());
    }
  }
}
