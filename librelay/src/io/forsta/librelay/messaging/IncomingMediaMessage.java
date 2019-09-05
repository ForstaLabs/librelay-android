package io.forsta.librelay.messaging;

import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.attachments.PointerAttachment;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final String  from;
  private final String  body;
  private final long    sentTimeMillis;
  private final long    expiresIn;
  private final boolean expirationUpdate;
  private boolean endSession = false;
  private final String messageRef;
  private final int voteCount;
  private final String messageId;

  private final List<String>     to          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  protected IncomingMediaMessage(String from, String to, String body, long sentTimeMillis, long expiresIn)
  {
    this.from             = from;
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body;
    this.expiresIn        = expiresIn;
    this.expirationUpdate = false;
    this.messageRef = null;
    this.voteCount = 0;
    this.messageId = null;

    this.to.add(to);
  }

  public IncomingMediaMessage(String from,
                              String to,
                              long sentTimeMillis,
                              long expiresIn,
                              boolean expirationUpdate,
                              Optional<String> body,
                              Optional<List<SignalServiceAttachment>> attachments)
  {
    this(from, to, sentTimeMillis, expiresIn, expirationUpdate, body, attachments, null, 0, null);
  }

  public IncomingMediaMessage(String from,
                              String to,
                              long sentTimeMillis,
                              long expiresIn,
                              boolean expirationUpdate,
                              Optional<String> body,
                              Optional<List<SignalServiceAttachment>> attachments,
                              String messageRef,
                              int voteCount,
                              String messageId)
  {
    this.from             = from;
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body.orNull();
    this.expiresIn        = expiresIn;
    this.expirationUpdate = expirationUpdate;
    this.messageRef = messageRef;
    this.voteCount = voteCount;
    this.messageId = messageId;

    this.to.add(to);
    this.attachments.addAll(PointerAttachment.forPointers(attachments));
  }

  public String getBody() {
    return body;
  }

  public String getAddress() {
    return from;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public boolean isEndSession() {
    return endSession;
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
}
