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
package io.forsta.librelay;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.components.DeliveryStatusView;
import io.forsta.librelay.components.AlertView;
import io.forsta.librelay.components.FromTextView;
import io.forsta.librelay.components.ThumbnailView;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.DateUtils;
import io.forsta.librelay.util.ResUtil;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.ViewUtil;

import java.util.Locale;
import java.util.Set;

import static io.forsta.librelay.util.SpanUtil.color;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements Recipients.RecipientsModifiedListener, Recipient.RecipientModifiedListener,
                                             BindableConversationListItem, Unbindable
{
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private Set<Long>          selectedThreads;
  private Recipients         recipients;
  private Recipient sender;
  private long               threadId;
  private TextView           subjectView;
  private FromTextView       fromView;
  private TextView           dateView;
  private TextView           archivedView;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;

  private boolean         read;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;

  private final @DrawableRes int readBackground;
  private final @DrawableRes int unreadBackround;

  private final Handler handler = new Handler();
  private int distributionType;
  private MaterialColor threadColor;
  private String threadTitle;
  private SpannableString threadDisplayBody;
  private boolean isAnnouncement = false;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    readBackground  = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_read);
    unreadBackround = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_unread);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectView             = (TextView)           findViewById(R.id.subject);
    this.fromView                = (FromTextView)       findViewById(R.id.from);
    this.dateView                = (TextView)           findViewById(R.id.date);
    this.deliveryStatusIndicator = (DeliveryStatusView) findViewById(R.id.delivery_status);
    this.alertView               = (AlertView)          findViewById(R.id.indicators_parent);
    this.contactPhotoImage       = (AvatarImageView)    findViewById(R.id.contact_photo_image);
    this.thumbnailView           = (ThumbnailView)      findViewById(R.id.thumbnail);
    this.archivedView            = ViewUtil.findById(this, R.id.archived);
    thumbnailView.setClickable(false);

    ViewUtil.setTextViewGravityStart(this.fromView, getContext());
    ViewUtil.setTextViewGravityStart(this.subjectView, getContext());
  }

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode)
  {
    this.selectedThreads  = selectedThreads;
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.read             = thread.isRead();
    this.distributionType = thread.getDistributionType();
    this.recipients.addListener(this);
    if (thread.getThreadType() == ThreadDatabase.ThreadTypes.ANNOUNCEMENT) {
      this.threadColor = MaterialColor.ANNOUNCEMENT;
    } else {
      this.threadColor = thread.getColor();
    }

    isAnnouncement = thread.getThreadType() == ThreadDatabase.ThreadTypes.ANNOUNCEMENT;
    threadTitle = thread.getTitle();
    threadDisplayBody = thread.getDisplayBody();
    String senderAddress = thread.getSenderAddress();
    if (!TextUtils.isEmpty(senderAddress)) {
      sender = RecipientFactory.getRecipient(getContext(), senderAddress, true);
    }
    setSubjectView(recipients, sender, threadDisplayBody, read);
    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(read ? date : color(getResources().getColor(R.color.textsecure_primary), date));
      dateView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setFromView(recipients, read);
    setStatusIcons(thread);
    setThumbnailSnippet(thread);
    setBatchState(batchMode);
    setBackground(thread);
    setRippleColor(threadColor);
    setAvatarImage();
  }

  @Override
  public void unbind() {
    if (this.recipients != null) this.recipients.removeListener(this);
    if (this.sender != null) this.sender.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean getRead() {
    return read;
  }

  public int getDistributionType() {
    return distributionType;
  }

  private void setThumbnailSnippet(ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      subjectParams.addRule(RelativeLayout.START_OF, R.id.thumbnail);

      this.subjectView.setLayoutParams(subjectParams);
      this.post(new ThumbnailPositioner(thumbnailView, archivedView, deliveryStatusIndicator, dateView));
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.delivery_status);
      subjectParams.addRule(RelativeLayout.START_OF, R.id.delivery_status);

      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (!thread.isOutgoing()) {
      deliveryStatusIndicator.setNone();
      alertView.setNone();
    } else if (thread.isFailed()) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else {
      alertView.setNone();
      if (thread.isDelivered()) deliveryStatusIndicator.setDelivered();
      else if (thread.isPending()) deliveryStatusIndicator.setPending();
      else deliveryStatusIndicator.setSent();
    }

    if (thread.isPinned()) {
      alertView.setPinned();
    }
  }

  private void setBackground(ThreadRecord thread) {
    if (thread.isRead()) setBackgroundResource(readBackground);
    else                 setBackgroundResource(unreadBackround);
  }

  private void setRippleColor(Recipients recipients) {
    ((RippleDrawable)(getBackground()).mutate())
        .setColor(ColorStateList.valueOf(recipients.getColor().toConversationColor(getContext())));
  }

  private void setRippleColor(MaterialColor color) {
    ((RippleDrawable)(getBackground()).mutate())
        .setColor(ColorStateList.valueOf(color.toConversationColor(getContext())));
  }

  @Override
  public void onModified(final Recipients recipients) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        setFromView(recipients, read);
        setRippleColor(threadColor);
        setAvatarImage();
      }
    });
  }

  private void setAvatarImage() {
    if (isAnnouncement) {
      contactPhotoImage.setAnnouncement();
    } else {
      contactPhotoImage.setAvatar(recipients, threadColor);
    }
  }

  private void setFromView(Recipients recipients, boolean read) {
    if (!TextUtils.isEmpty(threadTitle)) {
      fromView.setText(threadTitle, read);
    } else if (isAnnouncement) {
      fromView.setText(R.string.ConversationActivity_announcement);
    } else {
      fromView.setText(recipients, read);
    }
  }

  private void setSubjectView(Recipients recipients, Recipient sender, SpannableString body, boolean read) {
    if (sender != null) {
      sender.addListener(this);
      String name = TextSecurePreferences.getLocalAddress(getContext()).equals(sender.getAddress()) ? "" : sender.getName();
      if (!TextUtils.isEmpty(name) && recipients.getRecipientsList().size() > 1) {
        subjectView.setText(name + ": " + body);
      } else {
        subjectView.setText(body);
      }
    } else {
      subjectView.setText(threadDisplayBody);
    }

    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
  }

  @Override
  public void onModified(final Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        setSubjectView(recipients, recipient, threadDisplayBody, read);
      }
    });
  }

  private static class ThumbnailPositioner implements Runnable {
    private final View thumbnailView;
    private final View archivedView;
    private final View deliveryStatusView;
    private final View dateView;

    public ThumbnailPositioner(View thumbnailView, View archivedView, View deliveryStatusView, View dateView) {
      this.thumbnailView      = thumbnailView;
      this.archivedView       = archivedView;
      this.deliveryStatusView = deliveryStatusView;
      this.dateView           = dateView;
    }

    @Override
    public void run() {
      LayoutParams thumbnailParams = (RelativeLayout.LayoutParams)thumbnailView.getLayoutParams();

      if (archivedView.getVisibility() == View.VISIBLE &&
          (archivedView.getWidth() + deliveryStatusView.getWidth()) > dateView.getWidth())
      {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.delivery_status);
        thumbnailParams.addRule(RelativeLayout.START_OF, R.id.delivery_status);
      } else {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.date);
        thumbnailParams.addRule(RelativeLayout.START_OF, R.id.date);
      }

      thumbnailView.setLayoutParams(thumbnailParams);
    }
  }
}
