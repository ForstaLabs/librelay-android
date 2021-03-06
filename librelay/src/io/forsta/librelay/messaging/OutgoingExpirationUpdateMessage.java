package io.forsta.librelay.messaging;

import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.recipients.Recipients;

import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipients recipients, long sentTimeMillis, long expiresIn) {
    super(recipients, "", new LinkedList<Attachment>(), sentTimeMillis, expiresIn);
  }

  public OutgoingExpirationUpdateMessage(Recipients recipients, String body, long sentTimeMillis, long expiresIn) {
    super(recipients, body, new LinkedList<Attachment>(), sentTimeMillis, expiresIn);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
