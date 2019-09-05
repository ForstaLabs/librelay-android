/**
 * Copyright (C) 2012 Moxie Marlinpsike
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
package io.forsta.librelay.database.model;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.media.DocumentSlide;
import io.forsta.librelay.media.Slide;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.util.InvalidMessagePayloadException;
import io.forsta.librelay.R;
import io.forsta.librelay.database.documents.NetworkFailure;
import io.forsta.librelay.database.documents.IdentityKeyMismatch;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.ExpirationUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The base class for message record models that are displayed in
 * conversations, as opposed to models that are displayed in a thread list.
 * Encapsulates the shared data between both SMS and MMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class MessageRecord extends DisplayRecord {

  private final String TAG = MessageRecord.class.getSimpleName();

  private static final int MAX_DISPLAY_LENGTH = 2000;

  private final Recipient individualRecipient;
  private final int                       recipientDeviceId;
  private final long                      id;
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final long                      expiresIn;
  private final long                      expireStarted;
  private final String messageRef;
  private final int voteCount;
  private final String messageId;
  private final Context context;
  private final int partCount;
  private final @NonNull
  SlideDeck slideDeck;
  private final Map<String, MessageReceipt> receipts = new HashMap<>();

  public MessageRecord(Context context, long id, Recipients recipients,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, int receiptCount,
                long threadId, String body,
                @NonNull SlideDeck slideDeck,
                int partCount, long mailbox,
                List<IdentityKeyMismatch> mismatches,
                List<NetworkFailure> failures,
                long expiresIn, long expireStarted, String messageRef, int voteCount, String messageId, List<MessageReceipt> receipts)
  {
    super(context, body, recipients, dateSent, dateReceived, threadId, -1, receiptCount, mailbox);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.recipientDeviceId   = recipientDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = failures;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.messageRef = messageRef;
    this.voteCount = voteCount;
    this.messageId = messageId;
    this.context   = context.getApplicationContext();
    this.partCount = partCount;
    this.slideDeck = slideDeck;
    for (MessageReceipt receipt : receipts) {
      this.receipts.put(receipt.getAddress(), receipt);
    }
  }

  public MessageReceipt getMessageReceipt(String address) {
    return receipts.get(address);
  }

  public boolean isMms() {
    return true;
  }

  public boolean isSecure() {
    return MessageDatabase.Types.isSecureType(type);
  }

  public boolean isMentioned(Context context) {
    try {
      AtlasUser user = AtlasUser.getLocalUser(context);
      RelayContent forstaBody = getRelayContentBody();
      if (forstaBody.getMentions().contains(user.getUid())) {
        return true;
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean isNamed(Context context) {
    try {
      AtlasUser user = AtlasUser.getLocalUser(context);
      RelayContent forstaBody = getRelayContentBody();
      String plainTextBody = forstaBody.getTextBody().toLowerCase();
      String name = user.getName();
      if (name != null) {
        String[] parts = name.split(" ");
        for (String part : parts) {
          if (plainTextBody.contains(part.toLowerCase())) {
            return true;
          }
        }
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return false;
  }

  public String getPlainTextBody() {
    try {
      RelayContent forstaBody = getRelayContentBody();

      if(forstaBody.getVote() > 0) {
        return "Up Vote";
      }
      return forstaBody.getTextBody();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return "Invalid message body";
  }

  public Spanned getHtmlBody() {
    try {
      RelayContent forstaBody = getRelayContentBody();
      if (!TextUtils.isEmpty(forstaBody.getHtmlBody())) {
        return Html.fromHtml(forstaBody.getHtmlBody());
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String getGiphy() {
    try {
      RelayContent forstaBody = getRelayContentBody();
      return forstaBody.getGiphyUrl();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return "";
  }

  public String getMessageId() {
    try {
      if (TextUtils.isEmpty(messageId)) {
        return getRelayContentBody().getMessageId();
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return messageId;
  }

  public String getMessageRef() {
    return messageRef;
  }

  public int getVoteCount() {
    return voteCount;
  }

  @Override
  public SpannableString getDisplayBody() {
    String body = getBody();

    if (isExpirationTimerUpdate()) {
      String sender = isOutgoing() ? context.getString(R.string.MessageRecord_you) : getIndividualRecipient().toShortString();
      String time   = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
      return emphasisAdded(context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, sender, time));
    } else {
      try {
        RelayContent forstaBody = getRelayContentBody();
        body = !TextUtils.isEmpty(forstaBody.getHtmlBody()) ? forstaBody.getHtmlBody() : forstaBody.getTextBody();
      } catch (InvalidMessagePayloadException e) {
        e.printStackTrace();
      }
    }
    return new SpannableString(body);
  }

  public long getId() {
    return id;
  }

  public boolean isPush() {
    return MessageDatabase.Types.isPushType(type);
  }

  public long getTimestamp() {
    if (isPush() && getDateSent() < getDateReceived()) {
      return getDateSent();
    }
    return getDateReceived();
  }

  public boolean isStaleKeyExchange() {
    return MessageDatabase.Types.isStaleKeyExchange(type);
  }

  public boolean isProcessedKeyExchange() {
    return MessageDatabase.Types.isProcessedKeyExchange(type);
  }

  public boolean isIdentityMismatchFailure() {
    return mismatches != null && !mismatches.isEmpty();
  }

  public boolean isBundleKeyExchange() {
    return MessageDatabase.Types.isBundleKeyExchange(type);
  }

  public boolean isIdentityUpdate() {
    return MessageDatabase.Types.isIdentityUpdate(type);
  }

  public boolean isCorruptedKeyExchange() {
    return MessageDatabase.Types.isCorruptedKeyExchange(type);
  }

  public boolean isInvalidVersionKeyExchange() {
    return MessageDatabase.Types.isInvalidVersionKeyExchange(type);
  }

  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  public String getDocumentAttachmentFileName() {
    DocumentSlide documentSlide = getSlideDeck().getDocumentSlide();
    String fileName = documentSlide.getFileName().or(context.getString(R.string.DocumentView_unknown_file));
    try {
      RelayContent relayContent = getRelayContentBody();
      for (RelayContent.ForstaAttachment attachment : relayContent.getAttachments()) {
        if (documentSlide.getContentType().equals(attachment.getType())) {
          fileName = !TextUtils.isEmpty(attachment.getName()) ? attachment.getName() : fileName;
          break;
        }
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }

    return fileName;
  }

  public @NonNull SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public int getRecipientDeviceId() {
    return recipientDeviceId;
  }

  public long getType() {
    return type;
  }

  public List<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return mismatches;
  }

  public List<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public boolean hasNetworkFailures() {
    return networkFailures != null && !networkFailures.isEmpty();
  }

  protected SpannableString emphasisAdded(String sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new RelativeSizeSpan(0.9f), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  public boolean equals(Object other) {
    return other != null                              &&
           other instanceof MessageRecord             &&
           ((MessageRecord) other).getId() == getId() &&
           ((MessageRecord) other).isMms() == isMms();
  }

  public int hashCode() {
    return (int)getId();
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getExpireStarted() {
    return expireStarted;
  }

  public String getMessageBody() {
    if (!TextUtils.isEmpty(getHtmlBody())) {
      return getHtmlBody().toString();
    }
    return getPlainTextBody();
  }
}
