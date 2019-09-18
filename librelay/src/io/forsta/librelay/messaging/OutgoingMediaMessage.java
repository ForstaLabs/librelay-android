package io.forsta.librelay.messaging;

import android.text.TextUtils;

import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.recipients.Recipients;

import java.util.List;

public class OutgoingMediaMessage {

  private   final Recipients       recipients;
  protected String                 body;
  protected final List<Attachment> attachments;
  private   final long             sentTimeMillis;
  private   final long             expiresIn;
  private final String messageRef;
  private final int voteCount;
  private final String messageId;

  public OutgoingMediaMessage(Recipients recipients, String body, List<Attachment> attachments, long sentTimeMillis, long expiresIn) {
    this.recipients       = recipients;
    this.body             = body;
    this.sentTimeMillis   = sentTimeMillis;
    this.attachments      = attachments;
    this.expiresIn        = expiresIn;
    this.messageRef = null;
    this.voteCount = 0;
    this.messageId = null;
  }

  public OutgoingMediaMessage(Recipients recipients, String body, List<Attachment> attachments, long sentTimeMillis, long expiresIn, String messageUid, String messageRef, int vote) {
    this.recipients       = recipients;
    this.body             = body;
    this.sentTimeMillis   = sentTimeMillis;
    this.attachments      = attachments;
    this.expiresIn        = expiresIn;
    this.messageRef = messageRef;
    this.voteCount = vote;
    this.messageId = messageUid;
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public String getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public boolean isSecure() {
    return true;
  }

  public boolean isEndSession() {
    return false;
  }

  public boolean isKeyExchange() {
    return false;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isExpirationUpdate() {
    return false;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public String getMessageRef() {
    return messageRef;
  }

  public String getMessageId() {
    return messageId;
  }

  public int getVoteCount() {
    return voteCount;
  }

  private static String buildMessage(SlideDeck slideDeck, String message) {
    if (!TextUtils.isEmpty(message) && !TextUtils.isEmpty(slideDeck.getBody())) {
      return slideDeck.getBody() + "\n\n" + message;
    } else if (!TextUtils.isEmpty(message)) {
      return message;
    } else {
      return slideDeck.getBody();
    }
  }
}
