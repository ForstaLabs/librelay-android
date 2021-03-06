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

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import io.forsta.librelay.crypto.AcceptIdentityMismatch;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.components.AlertView;
import io.forsta.librelay.components.AudioView;
import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.components.ReplyListView;
import io.forsta.librelay.components.DeliveryStatusView;
import io.forsta.librelay.components.DocumentView;
import io.forsta.librelay.components.ExpirationTimerView;
import io.forsta.librelay.components.ThumbnailView;
import io.forsta.librelay.database.AttachmentDatabase;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.documents.IdentityKeyMismatch;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.media.DocumentSlide;
import io.forsta.librelay.media.Slide;
import io.forsta.librelay.media.SlideClickListener;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.service.ExpiringMessageManager;
import io.forsta.librelay.util.DateUtils;
import io.forsta.librelay.util.SaveAttachmentTask;
import io.forsta.librelay.util.Util;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout
    implements Recipient.RecipientModifiedListener, Recipients.RecipientsModifiedListener, BindableConversationItem
{
  private final static String TAG = ConversationItem.class.getSimpleName();

  private MessageRecord messageRecord;
  private Locale locale;
  private Recipient recipient;
  private String messageId;

  private View bodyBubble;
  private ReplyListView listView;
  private LinearLayout replyBox;
  private TextView bodyText;
  private TextView dateText;
  private TextView indicatorText;
  private TextView recipientText;
  private ImageView secureImage;
  private AvatarImageView contactPhoto;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView alertView;

  private @NonNull Set<MessageRecord>  batchSelected = new HashSet<>();
  private @Nullable Recipients conversationRecipients;
  private @NonNull ThumbnailView mediaThumbnail;
  private @NonNull AudioView audioView;
  private @NonNull DocumentView documentView;
  private @NonNull ExpirationTimerView expirationTimer;
  private VideoView videoView;
  private int giphyLoopCounter = 0;

  private int defaultBubbleColor;
  private int defaultIncomingBubbleColor;

  private final PassthroughClickListener passthroughClickListener = new PassthroughClickListener();
  private final Context context;

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    this.bodyText                = findViewById(R.id.conversation_item_body);
    this.dateText                = findViewById(R.id.conversation_item_date);
    this.indicatorText           = findViewById(R.id.indicator_text);
    this.recipientText         = findViewById(R.id.group_message_status);
    this.secureImage             = findViewById(R.id.secure_indicator);
    this.deliveryStatusIndicator = findViewById(R.id.delivery_status);
    this.alertView               = findViewById(R.id.indicators_parent);
    this.contactPhoto            = findViewById(R.id.contact_photo);
    this.bodyBubble              = findViewById(R.id.body_bubble);
    this.mediaThumbnail          = findViewById(R.id.image_view);
    this.audioView               = findViewById(R.id.audio_view);
    this.documentView            = findViewById(R.id.document_view);
    this.expirationTimer         = findViewById(R.id.expiration_indicator);
    videoView                    = findViewById(R.id.item_video_view);
    this.listView                = findViewById(R.id.conversation_list_view);
    this.replyBox                = findViewById(R.id.reply_box);

    setOnClickListener(new ClickListener(null));
    AttachmentDownloadClickListener downloadClickListener = new AttachmentDownloadClickListener();

    mediaThumbnail.setDownloadClickListener(downloadClickListener);
    mediaThumbnail.setOnLongClickListener(passthroughClickListener);
    mediaThumbnail.setOnClickListener(passthroughClickListener);
    audioView.setDownloadClickListener(downloadClickListener);
    audioView.setOnLongClickListener(passthroughClickListener);
    documentView.setDownloadClickListener(downloadClickListener);
    documentView.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);
  }

  @Override
  public void bind(@NonNull MessageRecord      messageRecord,
                   @NonNull Locale             locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   @NonNull Recipients         conversationRecipients)
  {
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.batchSelected          = batchSelected;
    this.conversationRecipients = conversationRecipients;
    this.recipient              = messageRecord.getIndividualRecipient();
    this.messageId              = messageRecord.getMessageId();

    this.recipient.addListener(this);
    this.conversationRecipients.addListener(this);
    giphyLoopCounter = 0;

    setInteractionState(messageRecord);
    setBodyText(messageRecord);
    setBubbleState(messageRecord, recipient);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setRecipientText(messageRecord, recipient);
    setMinimumWidth();
    setMediaAttributes(messageRecord);
    setExpiration(messageRecord);
    setReply(messageRecord);
  }

  private void initializeAttributes() {
    final int[]      attributes = new int[] {R.attr.conversation_item_bubble_background,
                                             R.attr.conversation_list_item_background_selected,
                                             R.attr.conversation_item_background, R.attr.conversation_item_bubble_incoming_background};
    final TypedArray attrs      = context.obtainStyledAttributes(attributes);

    defaultBubbleColor = attrs.getColor(0, Color.WHITE);
    defaultIncomingBubbleColor = attrs.getColor(3, Color.DKGRAY);
    attrs.recycle();
  }

  @Override
  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
    }

    this.expirationTimer.stopAnimation();
    videoView.stopPlayback();
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  public void setOnThumbnailClickListener(ThumbnailClickListener listener) {
    mediaThumbnail.setThumbnailClickListener(new SlideClickListener() {
      @Override
      public void onClick(View v, Slide slide) {
        listener.onThumbnailClicked(messageRecord, slide);
      }
    });
  }

  public interface ThumbnailClickListener {
    void onThumbnailClicked(MessageRecord messsageRecord, Slide slide);
  }

  /// MessageRecord Attribute Parsers
  private void setBubbleState(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(defaultBubbleColor);
      setAudioViewTint(messageRecord, conversationRecipients);
    } else {
      bodyBubble.getBackground().setColorFilter(defaultIncomingBubbleColor, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(defaultIncomingBubbleColor);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord, Recipients recipients) {
    if (messageRecord.isOutgoing()) {
      audioView.setTint(recipients.getColor().toConversationColor(context));
    }
  }

  private void setInteractionState(MessageRecord messageRecord) {
    setSelected(batchSelected.contains(messageRecord));
    mediaThumbnail.setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
    mediaThumbnail.setClickable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
    mediaThumbnail.setLongClickable(batchSelected.isEmpty());
    bodyText.setAutoLinkMask(batchSelected.isEmpty() ? Linkify.ALL : 0);
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() &&
           messageRecord.getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasVideo(MessageRecord messageRecord) {
    return messageRecord.isMms() &&
        messageRecord.getSlideDeck().getVideoSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms() &&
           messageRecord.getSlideDeck().getThumbnailSlide() != null;
  }

  private boolean hasGiphy(MessageRecord messageRecord) {
    return !TextUtils.isEmpty(messageRecord.getGiphy());
  }

  private boolean hasDocument(MessageRecord messageRecord) {
    return messageRecord.isMms() && messageRecord.getSlideDeck().getDocumentSlide() != null;
  }

  private void setBodyText(MessageRecord messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);

    if (messageRecord.isEndSession()) {
      bodyText.setText(context.getString(R.string.ThreadRecord_secure_session_reset));
    } else if (!TextUtils.isEmpty(messageRecord.getHtmlBody())) {
      bodyText.setText(messageRecord.getHtmlBody());
    } else {
      bodyText.setText(messageRecord.getPlainTextBody());
    }
    bodyText.setVisibility(View.VISIBLE);
  }

  private void setMediaAttributes(MessageRecord messageRecord) {
    boolean showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending());

    if (hasAudio(messageRecord)) {
      audioView.setVisibility(View.VISIBLE);
      mediaThumbnail.setVisibility(View.GONE);
      documentView.setVisibility(GONE);
      videoView.setVisibility(GONE);

      //noinspection ConstantConditions
      audioView.setAudio(messageRecord.getSlideDeck().getAudioSlide(), showControls);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    } else if (hasDocument(messageRecord)) {
      mediaThumbnail.setVisibility(View.GONE);
      audioView.setVisibility(View.GONE);
      documentView.setVisibility(VISIBLE);
      videoView.setVisibility(GONE);

      String attachmentFileName = messageRecord.getDocumentAttachmentFileName();
      DocumentSlide documentSlide = messageRecord.getSlideDeck().getDocumentSlide();
      documentView.setDocument(documentSlide, attachmentFileName);
      documentView.setDocumentClickListener(new DocumentAttachmentSaveClickListener(attachmentFileName));
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnail.setVisibility(View.VISIBLE);
      audioView.setVisibility(View.GONE);
      mediaThumbnail.hideVideoPlayButton();
      videoView.setVisibility(GONE);

      if (hasVideo(messageRecord)) {
        mediaThumbnail.showVideoPlayButton();
        audioView.setVisibility(View.GONE);
      }

      //noinspection ConstantConditions
      mediaThumbnail.setImageResource(messageRecord.getSlideDeck().getThumbnailSlide(),
                                      showControls);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    } else if (hasGiphy(messageRecord)){
      String giphy = messageRecord.getGiphy();
      mediaThumbnail.setVisibility(View.GONE);
      audioView.setVisibility(View.GONE);
      documentView.setVisibility(GONE);
      videoView.setVisibility(VISIBLE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      videoView.setVideoURI(Uri.parse(giphy));
      videoView.setOnPreparedListener((MediaPlayer mediaPlayer) -> {
          videoView.start();
        });
      videoView.setOnCompletionListener((MediaPlayer mediaPlayer) -> {
          if (giphyLoopCounter < 4) {
            videoView.start();
            giphyLoopCounter++;
          } else {
            giphyLoopCounter = 0;
          }
        });
      videoView.setOnErrorListener((MediaPlayer mediaPlayer, int i, int i1) -> {
          return true;
        });
    } else {
      videoView.setVisibility(GONE);
      mediaThumbnail.setVisibility(View.GONE);
      audioView.setVisibility(View.GONE);
      documentView.setVisibility(GONE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  private void setContactPhoto(Recipient recipient) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(recipient);
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    indicatorText.setVisibility(View.GONE);

    secureImage.setVisibility(View.VISIBLE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);
    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getTimestamp()));

    if (messageRecord.isFailed()) {
      setFailedStatusIcons();
    } else {
      alertView.setNone();

      if      (!messageRecord.isOutgoing()) deliveryStatusIndicator.setNone();
      else if (messageRecord.isDelivered()) deliveryStatusIndicator.setDelivered(); //Hack. Swapped this and pending to stop pending forever on untrusted identity processing.
      else if (messageRecord.isPending())   deliveryStatusIndicator.setPending();
      else                                  deliveryStatusIndicator.setSent();
    }
  }

  private void setExpiration(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      this.expirationTimer.setVisibility(View.VISIBLE);
      this.expirationTimer.setPercentage(0);

      if (messageRecord.getExpireStarted() > 0) {
        this.expirationTimer.setExpirationTime(messageRecord.getExpireStarted(),
                                               messageRecord.getExpiresIn());
        this.expirationTimer.startAnimation();
      } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
            long                   id                = messageRecord.getId();
            boolean                mms               = messageRecord.isMms();

            DbFactory.getMessageDatabase(context).markExpireStarted(id);
            expirationManager.scheduleDeletion(id, messageRecord.getExpiresIn());
            return null;
          }
        }.execute();
      }
    } else {
      this.expirationTimer.setVisibility(View.GONE);
    }
  }

  //Checking for messageRef inside messages could be redundant since the query for getConversation inside MmsSmsDatabase
  //has already been changed to not display messages with messageRefs.
  private void setReply(MessageRecord messageRecord) {
    if(messageRecord.getMessageRef() == null){
      MessageDatabase db = DbFactory.getMessageDatabase(context);
      Cursor cursor = db.getReplies(messageId);
      listView.setStackFromBottom(true);

      if((cursor != null) && (cursor.getCount() > 0)) {
        listView.setClickable(false);  //Could set up long click to bring up option to upvote
        listView.setFocusable(false);
        replyBox.setVisibility(VISIBLE);
        listView.setVisibility(VISIBLE);

        ReplyListAdapter adapter = new ReplyListAdapter(context, R.layout.reply_list_view, cursor);
        listView.setAdapter(adapter);
      } else {
        replyBox.setVisibility(GONE);
        listView.setVisibility(GONE);
        cursor.close();
      }
    } else {
      replyBox.setVisibility(GONE);
      listView.setVisibility(GONE);
    }
  }

  private void setFailedStatusIcons() {
    alertView.setFailed();
    deliveryStatusIndicator.setNone();
    dateText.setText(R.string.ConversationItem_error_not_delivered);

    if (messageRecord.isOutgoing()) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setFallbackStatusIcons() {
    alertView.setPendingApproval();
    deliveryStatusIndicator.setNone();
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }

  private void setMinimumWidth() {
    if (indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density) + (int) (22.0 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() && messageRecord.isBundleKeyExchange();
  }

  private void setRecipientText(MessageRecord messageRecord, Recipient recipient) {
    if (!messageRecord.isOutgoing()) {
      this.recipientText.setText(recipient.toShortString());
      this.recipientText.setVisibility(View.VISIBLE);
    } else {
      this.recipientText.setVisibility(View.GONE);
    }
  }

  /// Helper Methods

  private void setContactPhotoForRecipient(final Recipient recipient) {
    if (contactPhoto == null) return;

    contactPhoto.setAvatar(recipient, true);
    contactPhoto.setVisibility(View.VISIBLE);
  }

  /// Event handlers

  private void handleApproveIdentity() {
    List<IdentityKeyMismatch> mismatches = messageRecord.getIdentityKeyMismatches();

    if (mismatches.size() != 1) {
      throw new AssertionError("Identity mismatch count: " + mismatches.size());
    }

    new AcceptIdentityMismatch(getContext(), messageRecord, mismatches.get(0)).execute();
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(() -> {
        setBubbleState(messageRecord, recipient);
        setContactPhoto(recipient);
        setRecipientText(messageRecord, recipient);
    });
  }

  @Override
  public void onModified(final Recipients recipients) {
    Util.runOnMain(() -> {
        setAudioViewTint(messageRecord, recipients);
      });
  }

  private class DocumentAttachmentSaveClickListener implements SlideClickListener {
    private final String fileName;
    public DocumentAttachmentSaveClickListener(String fileName) {
      this.fileName = fileName;
    }

    @Override
    public void onClick(View v, final Slide slide) {
      SaveAttachmentTask.showWarningDialog(getContext(), (DialogInterface dialog, int which) -> {
          if (slide.getUri() != null) {
            SaveAttachmentTask saveTask = new SaveAttachmentTask(getContext());
            saveTask.execute(new SaveAttachmentTask.Attachment(slide.getUri(), slide.getContentType(), messageRecord.getDateReceived(), fileName));
          } else {
            Log.w(TAG, "No slide with attachable media found, failing nicely.");
            Toast.makeText(getContext(),
                getResources().getQuantityString(R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card, 1),
                Toast.LENGTH_LONG).show();
          }
        });
    }
  }

  private class AttachmentDownloadClickListener implements SlideClickListener {
    @Override public void onClick(View v, final Slide slide) {
      DbFactory.getAttachmentDatabase(context).setTransferState(messageRecord.getId(),
                                                                      slide.asAttachment(),
                                                                      AttachmentDatabase.TRANSFER_PROGRESS_STARTED);
    }
  }

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      performLongClick();
      return true;
    }

    @Override
    public void onClick(View v) {
      performClick();
    }
  }

  private class ClickListener implements View.OnClickListener {
    private OnClickListener parent;

    public ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (videoView != null && !videoView.isPlaying()) {
        videoView.start();
      }
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (!messageRecord.isOutgoing() && messageRecord.isIdentityMismatchFailure()) {
        handleApproveIdentity();
      }
    }
  }
}
