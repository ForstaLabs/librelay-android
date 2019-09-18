/**
 * Copyright (C) 2012 Moxie Marlinspike
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
import android.text.SpannableString;

import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.messaging.MessageManager;
import io.forsta.librelay.util.InvalidMessagePayloadException;
import io.forsta.librelay.recipients.Recipients;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DisplayRecord {

  protected final Context context;
  protected final long type;

  private final Recipients recipients;
  private final long       dateSent;
  private final long       dateReceived;
  private final long       threadId;
  private final String body;
  private final int        deliveryStatus;
  private final int        receiptCount;

  protected RelayContent relayContentBody;

  public DisplayRecord(Context context, String body, Recipients recipients, long dateSent,
                       long dateReceived, long threadId, int deliveryStatus, int receiptCount, long type)
  {
    this.context              = context.getApplicationContext();
    this.threadId             = threadId;
    this.recipients           = recipients;
    this.dateSent             = dateSent;
    this.dateReceived         = dateReceived;
    this.type                 = type;
    this.body                 = body;
    this.receiptCount         = receiptCount;
    this.deliveryStatus       = deliveryStatus;
  }

  public String getBody() {
    return body;
  }

  protected synchronized RelayContent getRelayContentBody() throws InvalidMessagePayloadException {
    if (relayContentBody == null) {
      relayContentBody = MessageManager.fromMessagBodyString(getBody());
    }
    return relayContentBody;
  }

  public boolean isFailed() {
    return MessageDatabase.Types.isFailedMessageType(type);
  }

  public boolean isPending() {
    return MessageDatabase.Types.isPendingMessageType(type);
  }

  public boolean isOutgoing() {
    return MessageDatabase.Types.isOutgoingMessageType(type);
  }

  public abstract SpannableString getDisplayBody();

  public Recipients getRecipients() {
    return recipients;
  }

  public long getDateSent() {
    return dateSent;
  }

  public long getDateReceived() {
    return dateReceived;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean isKeyExchange() {
    return MessageDatabase.Types.isKeyExchangeType(type);
  }

  public boolean isEndSession() {
    return MessageDatabase.Types.isEndSessionType(type);
  }

  public boolean isExpirationTimerUpdate() {
    return MessageDatabase.Types.isExpirationTimerUpdate(type);
  }

  public int getDeliveryStatus() {
    return deliveryStatus;
  }

  public int getReceiptCount() {
    return receiptCount;
  }

  public boolean isDelivered() {
    return receiptCount > 0;
  }
}
