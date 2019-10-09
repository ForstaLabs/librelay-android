package io.forsta.librelay.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Action;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.bumptech.glide.Glide;

import io.forsta.librelay.R;
import io.forsta.librelay.contacts.avatars.BitmapContactPhoto;
import io.forsta.librelay.media.MediaStreamUriLoader;
import io.forsta.librelay.media.Slide;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.preferences.NotificationPrivacyPreference;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.BitmapUtil;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SingleRecipientNotificationBuilder extends AbstractNotificationBuilder {

  private static final String TAG = SingleRecipientNotificationBuilder.class.getSimpleName();

  private final List<CharSequence> messageBodies = new LinkedList<>();

  private       SlideDeck    slideDeck;
  private CharSequence contentTitle;
  private CharSequence contentText;

  public SingleRecipientNotificationBuilder(@NonNull Context context,
                                            @NonNull NotificationPrivacyPreference privacy)
  {
    super(context, privacy);
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }

  public void setThread(@NonNull final Recipients recipients, String title) {
    if (privacy.isDisplayContact()) {
      if (!TextUtils.isEmpty(title)) {
        setContentTitle(title);
      } else {
        setContentTitle(recipients.toCondensedString(context));
      }

      if (recipients.isSingleRecipient() && recipients.getPrimaryRecipient().getContactUri() != null) {
        addPerson(recipients.getPrimaryRecipient().getContactUri().toString());
      }

      String gravatar = recipients.getPrimaryRecipient().getGravitarUrl();
      if (recipients.isSingleRecipient() && !TextUtils.isEmpty(gravatar)) {
        try {
          Bitmap bitmap = Glide.with(context).load(recipients.getPrimaryRecipient().getGravitarUrl()).asBitmap().into(-1, -1).get();
          setLargeIcon(new BitmapContactPhoto(bitmap).asDrawable(context, recipients.getColor().toConversationColor(context)));
        } catch (InterruptedException | ExecutionException e) {
          Log.w(TAG, e.getMessage() + " URL: " + gravatar);
        }
      } else {
        setLargeIcon(recipients.getContactPhoto()
            .asDrawable(context, recipients.getColor()
            .toConversationColor(context)));
      }

    } else {
      setContentTitle(context.getString(R.string.SingleRecipientNotificationBuilder_signal));
      setLargeIcon(recipients.getContactPhoto()
          .asDrawable(context, recipients.getColor()
              .toConversationColor(context)));
    }
  }

  public void setMessageCount(int messageCount) {
    setContentInfo(String.valueOf(messageCount));
    setNumber(messageCount);
  }

  public void setPrimaryMessageBody(@NonNull  Recipients threadRecipients,
                                    @NonNull  Recipient individualRecipient,
                                    @NonNull  CharSequence message,
                                    @Nullable SlideDeck slideDeck)
  {
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

    if (privacy.isDisplayContact() && (!threadRecipients.isSingleRecipient())) {
      stringBuilder.append(Util.getBoldedString(individualRecipient.toShortString() + ": "));
    }

    if (privacy.isDisplayMessage()) {
      setContentText(stringBuilder.append(message == null ? "" : message));
      this.slideDeck = slideDeck;
    } else {
      setContentText(stringBuilder.append(context.getString(R.string.SingleRecipientNotificationBuilder_new_message)));
    }
  }

  public void addActions(@NonNull PendingIntent markReadIntent,
                         @NonNull PendingIntent quickReplyIntent)
  {
    Action markAsReadAction = new Action(R.drawable.check,
                                         context.getString(R.string.MessageNotifier_mark_read),
                                         markReadIntent);

    Action replyAction = new Action(R.drawable.ic_reply_white_36dp,
        context.getString(R.string.MessageNotifier_reply),
        quickReplyIntent);

    addAction(markAsReadAction);
    addAction(replyAction);
  }

  public void addMessageBody(@NonNull Recipients threadRecipients,
                             @NonNull Recipient individualRecipient,
                             @Nullable CharSequence messageBody)
  {
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

    if (privacy.isDisplayContact() && (!threadRecipients.isSingleRecipient())) {
      stringBuilder.append(Util.getBoldedString(individualRecipient.toShortString() + ": "));
    }

    if (privacy.isDisplayMessage()) {
      messageBodies.add(stringBuilder.append(messageBody == null ? "" : messageBody));
    } else {
      messageBodies.add(stringBuilder.append(context.getString(R.string.SingleRecipientNotificationBuilder_new_message)));
    }
  }

  @Override
  public Notification build() {
    if (privacy.isDisplayMessage()) {
      if (messageBodies.size() == 1 && hasBigPictureSlide(slideDeck)) {
        setStyle(new NotificationCompat.BigPictureStyle()
                     .bigPicture(getBigPicture(slideDeck))
                     .setSummaryText(getBigText(messageBodies)));
      } else {
        setStyle(new NotificationCompat.BigTextStyle().bigText(getBigText(messageBodies)));
      }
    }

    return super.build();
  }

  private void setLargeIcon(@Nullable Drawable drawable) {
    if (drawable != null) {
      int    largeIconTargetSize  = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);
      Bitmap recipientPhotoBitmap = BitmapUtil.createFromDrawable(drawable, largeIconTargetSize, largeIconTargetSize);

      if (recipientPhotoBitmap != null) {
        setLargeIcon(recipientPhotoBitmap);
      }
    }
  }

  private boolean hasBigPictureSlide(@Nullable SlideDeck slideDeck) {
    if (slideDeck == null) {
      return false;
    }

    Slide thumbnailSlide = slideDeck.getThumbnailSlide();

    return thumbnailSlide != null         &&
           thumbnailSlide.hasImage()      &&
           !thumbnailSlide.isInProgress() &&
           thumbnailSlide.getThumbnailUri() != null;
  }

  private Bitmap getBigPicture(@NonNull SlideDeck slideDeck)
  {
    try {
      @SuppressWarnings("ConstantConditions")
      Uri uri = slideDeck.getThumbnailSlide().getThumbnailUri();

      return Glide.with(context)
                  .load(new MediaStreamUriLoader.AttachmentUri(uri))
                  .asBitmap()
                  .into(500, 500)
                  .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new AssertionError(e);
    }
  }

  private CharSequence getBigText(List<CharSequence> messageBodies) {
    SpannableStringBuilder content = new SpannableStringBuilder();

    for (CharSequence message : messageBodies) {
      content.append(message);
      content.append('\n');
    }

    return content;
  }

}
