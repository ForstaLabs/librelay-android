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
package io.forsta.librelay.messaging;

import android.content.Context;
import android.util.Log;

import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.jobmanager.JobManager;
import io.forsta.librelay.jobs.PushMediaSendJob;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.service.ExpiringMessageManager;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

import java.util.LinkedList;

import ws.com.google.android.mms.MmsException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  private static long send(final Context context,
                          final OutgoingMediaMessage message,
                          final long threadId)
  {
    try {
      MessageDatabase database       = DbFactory.getMessageDatabase(context);

      if (threadId == -1) {
        throw new Exception("Invalid thread id");
      }
      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(message, threadId);

      sendMediaMessage(context, recipients, messageId, message.getExpiresIn());

    } catch (MmsException e) {
      Log.w(TAG, e);
    } catch (Exception e) {
      Log.w(TAG, "Message send exception: " + e);
    }
    return threadId;
  }

  private static void sendControlMessage(final Context context, final OutgoingMediaMessage message) {
    try {
      MessageDatabase database       = DbFactory.getMessageDatabase(context);

      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(message, -1);

      sendMediaMessage(context, recipients, messageId, message.getExpiresIn());

    } catch (MmsException e) {
      Log.w(TAG, e);
    } catch (Exception e) {
      Log.w(TAG, "Message send exception: " + e);
    }
  }

  public static void resend(Context context, MessageRecord messageRecord) {
    try {
      long       messageId   = messageRecord.getId();
      boolean    keyExchange = messageRecord.isKeyExchange();
      long       expiresIn   = messageRecord.getExpiresIn();

      Recipients recipients = DbFactory.getMessageReceiptDatabase(context).getRecipientsForId(messageId);
      sendMediaMessage(context, recipients, messageId, expiresIn);

    } catch (Exception e) {
      Log.w(TAG, e);
    }
  }

  private static void sendMediaMessage(Context context,
                                       Recipients recipients,
                                       long messageId, long expiresIn)
  {
    if (isSelfSend(context, recipients)) {
      sendMediaSelf(context, messageId, expiresIn);
    } else {
      sendMediaPush(context, recipients, messageId);
    }
  }

  private static void sendMediaSelf(Context context,
                                    long messageId, long expiresIn)
  {
    ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MessageDatabase database               = DbFactory.getMessageDatabase(context);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    Recipient you = RecipientFactory.getRecipient(context, TextSecurePreferences.getLocalAddress(context), false);
    Recipients recipients = RecipientFactory.getRecipientsFor(context, you, false);
    sendMediaPush(context, recipients, messageId);

    if (expiresIn > 0) {
      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, expiresIn);
    }
  }

  private static void sendMediaPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushMediaSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress()));
  }

  private static boolean isSelfSend(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (!recipients.isSingleRecipient()) {
      return false;
    }

    return Util.isOwnNumber(context, recipients.getPrimaryRecipient().getAddress());
  }

  public static long sendContentMessage(Context context, String body, SlideDeck slideDeck, long threadId, long expiresIn) {
    OutgoingMediaMessage message = MessageManager.createOutgoingContentMessage(context, body, slideDeck.asAttachments(), threadId, expiresIn);
    return send(context, message, threadId);
  }

  public static long sendContentReplyMesage(Context context, String body, SlideDeck slideDeck, long threadId, long expiresIn, String messageRef, int vote) {
    OutgoingMediaMessage message = MessageManager.createOutgoingContentReplyMessage(context, body, slideDeck.asAttachments(), threadId, expiresIn, messageRef, vote);
    return send(context, message, threadId);
  }

  public static void sendExpirationUpdate(Context context, long threadId, int expirationTime) {
    OutgoingExpirationUpdateMessage message = MessageManager.createOutgoingExpirationUpdateMessage(context, threadId, expirationTime * 1000);
    send(context, message, threadId);
  }

  public static void sendThreadUpdate(Context context, long threadId) {
    try {
      ThreadRecord thread = DbFactory.getThreadDatabase(context).getThread(threadId);
      Recipients recipients = thread.getRecipients();
      AtlasUser user = AtlasUser.getLocalUser(context);
      String payload = MessageManager.createThreadUpdateMessage(context, user, thread);
      OutgoingMediaMessage message = new OutgoingMediaMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      sendControlMessage(context, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void sendEndSessionMessage(Context context, long threadId) {
    OutgoingEndSessionMediaMessage message = MessageManager.createOutgoingEndSessionMessage(context, threadId);
    send(context, message, threadId);
  }
}
