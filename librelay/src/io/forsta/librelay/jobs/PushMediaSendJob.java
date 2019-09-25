package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.model.RelayDistribution;
import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.MessageReceiptsDatabase;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.messaging.MessageManager;
import io.forsta.librelay.util.InvalidMessagePayloadException;
import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.crypto.storage.TextSecureSessionStore;
import io.forsta.librelay.database.NoSuchMessageException;
import io.forsta.librelay.database.documents.NetworkFailure;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.media.MediaConstraints;
import io.forsta.librelay.messaging.OutgoingMediaMessage;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.RecipientFormattingException;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.service.ExpiringMessageManager;
import io.forsta.librelay.transport.RetryLaterException;
import io.forsta.librelay.transport.UndeliverableMessageException;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.forsta.librelay.util.TextSecurePreferences;
import ws.com.google.android.mms.MmsException;

public class PushMediaSendJob extends PushSendJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushMediaSendJob.class.getSimpleName();
  private static final String KEY_MESSAGE_ID = "message_id";

  private long messageId;

  public PushMediaSendJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MessageDatabase messageDatabase = DbFactory.getMessageDatabase(context);
    messageDatabase.markAsSending(messageId);
    messageDatabase.markAsPush(messageId);
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    messageId = data.getLong(KEY_MESSAGE_ID);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_MESSAGE_ID, messageId).build();
  }

  @Override
  public void onSend()
      throws RetryLaterException, MmsException, IOException, NoSuchMessageException,
      UndeliverableMessageException, InvalidMessagePayloadException {

    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MessageDatabase database = DbFactory.getMessageDatabase(context);
    MessageReceiptsDatabase receiptsDatabase = DbFactory.getMessageReceiptDatabase(context);
    OutgoingMediaMessage outgoingMessage = database.getOutgoingMessage(messageId);
    RelayContent relayContent = MessageManager.fromMessagBodyString(outgoingMessage.getBody());
    RelayDistribution distribution = AtlasApi.getMessageDistribution(context, relayContent.getUniversalExpression());

    Log.d(TAG, "Outgoing message recipients: " + outgoingMessage.getRecipients().toFullString());

    List<OutgoingMediaMessage> messageQueue = new ArrayList<>();
    messageQueue.add(outgoingMessage);

    if (distribution.hasMonitors() && !outgoingMessage.isExpirationUpdate()) {
      Recipients monitors = RecipientFactory.getRecipientsFromStrings(context, distribution.getMonitors(context), false);
      OutgoingMediaMessage monitorMessage = new OutgoingMediaMessage(monitors, outgoingMessage.getBody(), outgoingMessage.getAttachments(), System.currentTimeMillis(), 0);
      messageQueue.add(monitorMessage);
    }

    for (OutgoingMediaMessage message : messageQueue) {
      try {
        deliver(message, message.getRecipients());
        database.markAsPush(messageId);
        database.markAsSecure(messageId);
        database.markAsSent(messageId);
        markAttachmentsUploaded(messageId, message.getAttachments());

        if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
          database.markExpireStarted(messageId);
          expirationManager.scheduleDeletion(messageId, message.getExpiresIn());
        }
      } catch (EncapsulatedExceptions e) {
        Log.w(TAG, e);

        List<NetworkFailure> failures = new LinkedList<>();
        for (NetworkFailureException nfe : e.getNetworkExceptions()) {
          Recipient recipient = RecipientFactory.getRecipientsFromString(context, nfe.getE164number(), false).getPrimaryRecipient();
          failures.add(new NetworkFailure(recipient.getRecipientId()));
          receiptsDatabase.updateFailed(messageId, recipient.getAddress());
        }

        List<String> untrustedRecipients = new ArrayList<>();
        for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
          untrustedRecipients.add(uie.getE164Number());
          acceptIndentityKey(uie);
        }

        if (untrustedRecipients.size() > 0) {
          Recipients failedRecipients = RecipientFactory.getRecipientsFromStrings(context, untrustedRecipients, false);
          try {
            deliver(message, failedRecipients);
          } catch (InvalidNumberException | EncapsulatedExceptions | RecipientFormattingException e1) {
            e1.printStackTrace();
          }
        }

        if (e.getUnregisteredUserExceptions().size() > 0) {
          for (UnregisteredUserException uue : e.getUnregisteredUserExceptions()) {
            Log.w(TAG, "Unregistered User: " + uue.getE164Number());
            receiptsDatabase.updateUnregisteredUser(messageId, uue.getE164Number());
          }
        }

        database.addFailures(messageId, failures);
        database.markAsPush(messageId);

        if (e.getNetworkExceptions().isEmpty()) {
          database.markAsSecure(messageId);
          database.markAsSent(messageId);
          markAttachmentsUploaded(messageId, message.getAttachments());
        } else {
          database.markAsSentFailed(messageId);
          notifyMediaMessageDeliveryFailed(context, messageId);
        }
      } catch (InvalidNumberException | Exception e) {
        Log.w(TAG, e);
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
      } 
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof RetryLaterException)        return true;

    return false;
  }

  @Override
  public void onCanceled() {
    DbFactory.getMessageDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private void deliver(OutgoingMediaMessage message, Recipients recipients)
      throws IOException, RecipientFormattingException, InvalidNumberException,
      EncapsulatedExceptions, UndeliverableMessageException
  {
    if (recipients == null || recipients.getPrimaryRecipient() == null || recipients.getPrimaryRecipient().getAddress() == null) {
      throw new UndeliverableMessageException("No destination address.");
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(message);
    List<SignalServiceAddress> addresses = getPushAddresses(recipients);
    Log.w(TAG, "Sending message: " + messageId);
    messageSender.sendMessage(addresses, mediaMessage);

    if (mediaMessage.isEndSession()) {
      Log.w(TAG, "Deleting sessions for: " + recipients.toShortString());
      SessionStore sessionStore = new TextSecureSessionStore(context);
      for (Recipient recipient : recipients) {
        sessionStore.deleteAllSessions(recipient.getAddress());
      }
    }
  }

  private void acceptIndentityKey(UntrustedIdentityException uie) {
    Log.w(TAG, uie);
    Log.w(TAG, "Media Message. Auto handling untrusted identity.");
    IdentityKey identityKey    = uie.getIdentityKey();
    DbFactory.getIdentities(context).saveIdentity(uie.getE164Number(), identityKey);
  }

  private SignalServiceDataMessage createSignalServiceDataMessage(OutgoingMediaMessage message) throws UndeliverableMessageException {
    List<Attachment>              scaledAttachments = scaleAttachments(MediaConstraints.PUSH_CONSTRAINTS, message.getAttachments());
    List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(scaledAttachments);
    SignalServiceDataMessage      mediaMessage      = SignalServiceDataMessage.newBuilder()
        .withBody(message.getBody())
        .withAttachments(attachmentStreams)
        .withTimestamp(message.getSentTimeMillis())
        .withExpiration((int)(message.getExpiresIn() / 1000))
        .asExpirationUpdate(message.isExpirationUpdate())
        .asEndSessionMessage(message.isEndSession())
        .build();
    return mediaMessage;
  }

  private List<SignalServiceAddress> getPushAddresses(Recipients recipients) throws InvalidNumberException {
    List<SignalServiceAddress> addresses = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      String localUid = TextSecurePreferences.getLocalAddress(context);
      if (!localUid.equals(recipient.getAddress())) {
        addresses.add(getPushAddress(recipient.getAddress()));
      }
    }

    return addresses;
  }
}
