package io.forsta.librelay.jobs;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.work.WorkerParameters;
import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.model.RelayDistribution;
import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.messaging.MessageManager;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.service.ForstaServiceAccountManager;
import io.forsta.librelay.util.InvalidMessagePayloadException;
import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.attachments.DatabaseAttachment;
import io.forsta.librelay.attachments.PointerAttachment;
import io.forsta.librelay.crypto.IdentityKeyUtil;
import io.forsta.librelay.crypto.SecurityEvent;
import io.forsta.librelay.crypto.storage.SignalProtocolStoreImpl;
import io.forsta.librelay.crypto.storage.TextSecureSessionStore;
import io.forsta.librelay.database.MessageDatabase.SyncMessageId;
import io.forsta.librelay.database.NoSuchMessageException;
import io.forsta.librelay.database.PushDatabase;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.messaging.IncomingMediaMessage;
import io.forsta.librelay.messaging.OutgoingExpirationUpdateMessage;
import io.forsta.librelay.messaging.OutgoingMediaMessage;
import io.forsta.librelay.notifications.MessageNotifier;
import io.forsta.librelay.messaging.TextSecureCommunicationFactory;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.service.WebRtcCallService;
import io.forsta.librelay.util.Base64;
import io.forsta.librelay.recipients.DirectoryHelper;
import io.forsta.librelay.util.TextSecurePreferences;

import org.webrtc.IceCandidate;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;
import ws.com.google.android.mms.MmsException;
import androidx.work.Data;

public class PushDecryptJob extends ContextJob {

  private static final long serialVersionUID = 2L;

  public static final String TAG = PushDecryptJob.class.getSimpleName();
  private static final String KEY_MESSAGE_ID = "message_id";

  private long messageId;

  public PushDecryptJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public PushDecryptJob(Context context, long pushMessageId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("__PUSH_DECRYPT_JOB__")
                                .create());
    this.messageId    = pushMessageId;
  }

  @Override
  public void onAdded() {}

  protected void initialize(@NonNull SafeData data) {
    messageId    = data.getLong(KEY_MESSAGE_ID);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_MESSAGE_ID, messageId)
        .build();
  }

  @Override
  public void onRun() throws NoSuchMessageException {
    synchronized (PushReceivedJob.RECEIVE_LOCK) {
      if (!IdentityKeyUtil.hasIdentityKey(context)) {
        Log.w(TAG, "Skipping job, waiting for migration...");
        return;
      }

      PushDatabase          database             = DbFactory.getPushDatabase(context);
      SignalServiceEnvelope envelope             = database.get(messageId);

      try {
        handleMessage(envelope);
        database.delete(messageId);
      } catch (Exception e) {
        Log.e(TAG, "Exception: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleMessage(SignalServiceEnvelope envelope) {
    synchronized (PushReceivedJob.RECEIVE_LOCK) {
      try {
        SignalProtocolStore  axolotlStore = new SignalProtocolStoreImpl(context);
        SignalServiceAddress localAddress = new SignalServiceAddress(TextSecurePreferences.getLocalAddress(context));
        SignalServiceCipher  cipher       = new SignalServiceCipher(localAddress, axolotlStore);

        SignalServiceContent content = null;
        try {
          content = cipher.decrypt(envelope);
        } catch (UntrustedIdentityException e) {
          SignalServiceCipher updatedCypher = autoHandleUntrustedIdentity(envelope, localAddress, axolotlStore);
          content = updatedCypher.decrypt(envelope);
        }

        if (content.getDataMessage().isPresent()) {
          SignalServiceDataMessage message = content.getDataMessage().get();

          if (message.isEndSession())                    handleEndSessionMessage(envelope, message);
          else if (message.isExpirationUpdate())         handleExpirationUpdate(envelope, message);
          else                                           handleMediaMessage(envelope, message);
        } else if (content.getSyncMessage().isPresent()) {
          SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

          if      (syncMessage.getSent().isPresent())    handleSynchronizeSentMessage(envelope, syncMessage.getSent().get());
          else if (syncMessage.getRead().isPresent())    handleSynchronizeReadMessage(syncMessage.getRead().get(), envelope.getTimestamp());
          else                                           Log.w(TAG, "Contains no known sync types...");
        }

        if (envelope.isPreKeySignalMessage()) {
          ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob(context));
        }
      } catch (InvalidVersionException e) {
        Log.w(TAG, e);
      } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
        Log.w(TAG, e);
//      handleCorruptMessage(masterSecret, envelope, smsMessageId);
      } catch (NoSessionException e) {
        Log.w(TAG, e);
//      handleNoSessionMessage(masterSecret, envelope, smsMessageId);
      } catch (LegacyMessageException e) {
        Log.w(TAG, e);
      } catch (DuplicateMessageException e) {
        Log.w(TAG, e);
//      handleDuplicateMessage(masterSecret, envelope, smsMessageId);
      } catch (UntrustedIdentityException e) {
        Log.w(TAG, e);
      } catch (InvalidMessagePayloadException e) {
        Log.e(TAG, "Invalid Forsta message body");
        e.printStackTrace();
      }
    }
  }

  private SignalServiceCipher autoHandleUntrustedIdentity(SignalServiceEnvelope envelope, SignalServiceAddress localAddress, SignalProtocolStore axolotlStore) throws InvalidVersionException, InvalidMessageException {
    Log.w(TAG, "Auto handling untrusted identity");
    Recipient recipient = RecipientFactory.getRecipient(context, envelope.getSource(), false);
    long recipientId = recipient.getRecipientId();
    Log.w(TAG, "From recipient: " + recipient.getAddress() + " " + recipient.getName());
    byte[] encryptedContent = (!envelope.hasLegacyMessage() && envelope.hasContent()) ? envelope.getContent() : envelope.getLegacyMessage();
    PreKeySignalMessage whisperMessage = new PreKeySignalMessage(encryptedContent);
    IdentityKey identityKey = whisperMessage.getIdentityKey();
    DbFactory.getIdentities(context).saveIdentity(envelope.getSource(), identityKey);
    return new SignalServiceCipher(localAddress, axolotlStore);
  }

  private void handleExpirationUpdate(@NonNull SignalServiceEnvelope envelope,
                                      @NonNull SignalServiceDataMessage message)
      throws MmsException, InvalidMessagePayloadException {
    MessageDatabase database     = DbFactory.getMessageDatabase(context);
    String               localNumber  = TextSecurePreferences.getLocalAddress(context);
    String                body       = message.getBody().isPresent() ? message.getBody().get() : "";
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(envelope.getSource(),
                                                                 localNumber, message.getTimestamp(),
                                                                 message.getExpiresInSeconds() * 1000, true,
                                                                 message.getBody(),
                                                                 Optional.<List<SignalServiceAttachment>>absent());

    RelayContent relayContent = MessageManager.fromMessagBodyString(body);
    long threadId = updateThreadDistribution(relayContent);

    database.insertSecureDecryptedMessageInbox(mediaMessage, threadId);
    DbFactory.getThreadPreferenceDatabase(context).setExpireMessages(threadId, message.getExpiresInSeconds());
  }

  private void handleEndSessionMessage(@NonNull SignalServiceEnvelope    envelope,
                                       @NonNull SignalServiceDataMessage message)
  {
    SignalProtocolAddress addr = new SignalProtocolAddress(envelope.getSource(), envelope.getSourceDevice());
    Log.w(TAG, "Deleting session for: " + addr);
    SessionStore sessionStore = new TextSecureSessionStore(context);
    // See if we can just archive the current session, instead of deleting it.
    sessionStore.deleteSession(addr);
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message)
  {
    String destination = message.getDestination().get();
    if (destination == null) {
      Log.e(TAG, "Invalid recipeint for end session");
    } else {
      Log.w(TAG, "Deleting sessions for: " + destination);
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(destination);
      SecurityEvent.broadcastSecurityUpdateEvent(context);
    }
    return -1;
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceEnvelope envelope,
                                            @NonNull SentTranscriptMessage message)
      throws MmsException, InvalidMessagePayloadException {
    Long threadId;

    if (message.getMessage().isEndSession()) {
      Log.e(TAG, "Sync end session is invalid: Only send directly to peers");
      //threadId = handleSynchronizeSentEndSessionMessage(message);
      threadId = -1L;
    } else if (message.getMessage().isExpirationUpdate()) {
      threadId = handleSynchronizeSentExpirationUpdate(message);
    } else {
      threadId = handleSynchronizeSentMediaMessage(envelope, message);
    }

    if (threadId != -1) {
      DbFactory.getThreadDatabase(getContext()).setRead(threadId);
      MessageNotifier.updateNotification(getContext());
    }
  }

  private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages,
                                            long envelopeTimestamp)
  {
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringMedia = DbFactory.getMessageDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()), envelopeTimestamp);

      for (Pair<Long, Long> expiringMessage : expiringMedia) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first,envelopeTimestamp, expiringMessage.second);
      }
    }

    MessageNotifier.updateNotification(context);
  }

  private void handleMediaMessage(@NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message)
      throws MmsException, InvalidMessagePayloadException {
    String                body       = message.getBody().isPresent() ? message.getBody().get() : "";
    RelayContent relayContent = MessageManager.fromMessagBodyString(body);
    relayContent.setSenderId(envelope.getSource());
    relayContent.setDeviceId(envelope.getSourceDevice());
    relayContent.setTimeStamp(envelope.getTimestamp());

    if (relayContent.getMessageType().equals(RelayContent.MessageTypes.CONTENT)) {
      handleContentMessage(relayContent, message, envelope);
    } else {
      handleControlMessage(relayContent);
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message)
      throws MmsException, InvalidMessagePayloadException {
    MessageDatabase database   = DbFactory.getMessageDatabase(context);

    RelayContent relayContent = MessageManager.fromMessagBodyString(message.getMessage().getBody().get());
    RelayDistribution distribution = AtlasApi.getMessageDistribution(context, relayContent.getUniversalExpression());
    Recipients recipients = getDistributionRecipients(distribution);
    long threadId = DbFactory.getThreadDatabase(context).getOrAllocateThreadId(relayContent, distribution);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipients,
        message.getTimestamp(),
        message.getMessage().getExpiresInSeconds() * 1000);

    long messageId = database.insertMessageOutbox(expirationUpdateMessage, threadId);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    DbFactory.getThreadPreferenceDatabase(context).setExpireMessages(threadId, message.getMessage().getExpiresInSeconds());

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull SignalServiceEnvelope envelope,
                                                 @NonNull SentTranscriptMessage message)
      throws MmsException, InvalidMessagePayloadException {
    MessageDatabase database     = DbFactory.getMessageDatabase(context);

    RelayContent relayContent = MessageManager.fromMessagBodyString(message.getMessage().getBody().get());
    relayContent.setTimeStamp(message.getTimestamp());
    relayContent.setSenderId(envelope.getSource());
    relayContent.setDeviceId(envelope.getSourceDevice());
    relayContent.setTimeStamp(envelope.getTimestamp());
    if (relayContent.getMessageType().equals(RelayContent.MessageTypes.CONTENT)) {
      RelayDistribution distribution = AtlasApi.getMessageDistribution(context, relayContent.getUniversalExpression());
      Recipients recipients = getDistributionRecipients(distribution);
      long threadId = updateThreadDistribution(relayContent);

      if (DbFactory.getThreadPreferenceDatabase(context).getExpireMessages(threadId) != message.getMessage().getExpiresInSeconds()) {
        handleSynchronizeSentExpirationUpdate(message);
      }

      OutgoingMediaMessage  mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
          PointerAttachment.forPointers(message.getMessage().getAttachments()),
          message.getTimestamp(),
          message.getMessage().getExpiresInSeconds() * 1000,
          relayContent.getMessageId(), relayContent.getMessageRef(), relayContent.getVote());

      long messageId = database.insertMessageOutbox(mediaMessage, threadId);

      database.markAsSent(messageId);
      database.markAsPush(messageId);

      for (DatabaseAttachment attachment : DbFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId)) {
        ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new AttachmentDownloadJob(context, messageId, attachment.getAttachmentId()));
      }

      if (message.getMessage().getExpiresInSeconds() > 0) {
        database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
        ApplicationContext.getInstance(context)
            .getExpiringMessageManager()
            .scheduleDeletion(messageId,
                message.getExpirationStartTimestamp(),
                message.getMessage().getExpiresInSeconds() * 1000);
      }

      return threadId;
    } else {
      Log.w(TAG, "handleSynchronizeSentMediaMessage Type: " + relayContent.getControlType());
      handleSyncControlMessage(relayContent);
      return -1;
    }
  }

  private Recipients getDistributionRecipients(RelayDistribution distribution) throws InvalidMessagePayloadException {
    if (distribution.hasRecipients()) {
      return RecipientFactory.getRecipientsFromStrings(context, distribution.getRecipients(context), false);
    }
    throw new InvalidMessagePayloadException("No recipients found in message.");
  }

  private long updateThreadDistribution(RelayContent relayContent) {
    RelayDistribution distribution = AtlasApi.getMessageDistribution(context, relayContent.getUniversalExpression());
    DirectoryHelper.refreshDirectoryFor(context, distribution.getRecipients(context));
    long threadId = DbFactory.getThreadDatabase(context).getOrAllocateThreadId(relayContent, distribution);
    return  threadId;
  }

  private void handleContentMessage(RelayContent relayContent,
                                    SignalServiceDataMessage message,
                                    SignalServiceEnvelope envelope) throws InvalidMessagePayloadException, MmsException {

    MessageDatabase database     = DbFactory.getMessageDatabase(context);
    String localAddress  = TextSecurePreferences.getLocalAddress(context);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(envelope.getSource(),
        localAddress, message.getTimestamp(),
        message.getExpiresInSeconds() * 1000, false,
        message.getBody(),
        message.getAttachments(), relayContent.getMessageRef(), relayContent.getVote(), relayContent.getMessageId());

    long threadId = updateThreadDistribution(relayContent);

    if (message.getExpiresInSeconds() != DbFactory.getThreadPreferenceDatabase(context).getExpireMessages(threadId)) {
      handleExpirationUpdate(envelope, message);
    }

    Pair<Long, Long>         messageAndThreadId = database.insertSecureDecryptedMessageInbox(mediaMessage, threadId);
    List<DatabaseAttachment> attachments        = DbFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageAndThreadId.first);

    for (DatabaseAttachment attachment : attachments) {
      ApplicationContext.getInstance(context)
          .getJobManager()
          .add(new AttachmentDownloadJob(context, messageAndThreadId.first,
              attachment.getAttachmentId()));
    }

    MessageNotifier.updateNotification(context, messageAndThreadId.second);
  }

  private void handleControlMessage(RelayContent relayContent) {
    try {
      Log.d(TAG, "handleControlMessage: " + relayContent.getControlType() + " " + relayContent.getSenderId() + ":" + relayContent.getDeviceId());

      if (relayContent.getControlType().equals(RelayContent.ControlTypes.PROVISION_REQUEST)) {
        Log.w(TAG, "Got Provision Request...");
        handleProvisionRequest(relayContent);
      } else {

        switch (relayContent.getControlType()) {
          case RelayContent.ControlTypes.THREAD_UPDATE:
            updateThreadDistribution(relayContent);
            break;
          case RelayContent.ControlTypes.READ_MARK:
            handleReadMark(relayContent);
            break;
          case RelayContent.ControlTypes.CALL_JOIN:
            updateThreadDistribution(relayContent);
            handleCallJoin(relayContent);
            break;
          case RelayContent.ControlTypes.CALL_OFFER:
            handleCallOffer(relayContent);
            break;
          case RelayContent.ControlTypes.CALL_ICE_CANDIDATES:
            handleCallIceCandidates(relayContent);
            break;
          case RelayContent.ControlTypes.CALL_LEAVE:
            handleCallLeave(relayContent);
            break;
          case RelayContent.ControlTypes.CALL_ACCEPT_OFFER:
            handleCallAcceptOffer(relayContent);
            break;
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Control message exception: " + e.getMessage());
    }
  }

  private void handleReadMark(RelayContent relayContent) {
    DbFactory.getMessageReceiptDatabase(context).updateRead(relayContent.getReadMark(), relayContent.getSenderId());
  }

  private void handleProvisionRequest(RelayContent relayContent) throws Exception {
    String sender = relayContent.getSenderId();
    if (!sender.equals(BuildConfig.FORSTA_SYNC_NUMBER)) {
      throw new Exception("Received provision request from unknown sender.");
    }
    RelayContent.ForstaProvisionRequest request = relayContent.getProvisionRequest();
    ForstaServiceAccountManager accountManager = TextSecureCommunicationFactory.createManager(context);
    String verificationCode = accountManager.getNewDeviceVerificationCode();
    String ephemeralId = request.getUuid();
    String theirPublicKeyEncoded = request.getKey();

    if (TextUtils.isEmpty(ephemeralId) || TextUtils.isEmpty(theirPublicKeyEncoded)) {
      throw new Exception("UUID or Key is empty!");
    }

    ECPublicKey theirPublicKey = Curve.decodePoint(Base64.decode(theirPublicKeyEncoded), 0);
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context);
    accountManager.addDevice(ephemeralId, theirPublicKey, identityKeyPair, verificationCode);
    TextSecurePreferences.setMultiDevice(context, true);
  }

  private void handleCallJoin(RelayContent relayContent) throws InvalidMessagePayloadException {
    RelayContent.ForstaCall callJoin = relayContent.getCall();
    Intent joinIntent = new Intent(context, WebRtcCallService.class);
    joinIntent.setAction(WebRtcCallService.ACTION_JOIN_CALL);
    joinIntent.putExtra(WebRtcCallService.EXTRA_CALL_ID, callJoin.getCallId());
    joinIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, relayContent.getSenderId());
    joinIntent.putExtra(WebRtcCallService.EXTRA_DEVICE_ID, relayContent.getDeviceId());
    joinIntent.putExtra(WebRtcCallService.EXTRA_THREAD_UID, relayContent.getThreadUId());
    joinIntent.putExtra(WebRtcCallService.EXTRA_TIMESTAMP, relayContent.getTimeStamp());
    joinIntent.putExtra(WebRtcCallService.EXTRA_PEER_ID, callJoin.getPeerId());
    List<String> joinMembers = callJoin.getCallMembers();
    joinIntent.putExtra(WebRtcCallService.EXTRA_CALL_MEMBERS, joinMembers.toArray(new String[joinMembers.size()]));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(joinIntent);
    else                                                context.startService(joinIntent);
  }

  private void handleCallOffer(RelayContent relayContent) throws InvalidMessagePayloadException {
    RelayContent.ForstaCall callOffer = relayContent.getCall();
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_CALL_OFFER);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, callOffer.getCallId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, relayContent.getSenderId());
    intent.putExtra(WebRtcCallService.EXTRA_DEVICE_ID, relayContent.getDeviceId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, callOffer.getOffer());
    intent.putExtra(WebRtcCallService.EXTRA_THREAD_UID, relayContent.getThreadUId());
    intent.putExtra(WebRtcCallService.EXTRA_TIMESTAMP, relayContent.getTimeStamp());
    intent.putExtra(WebRtcCallService.EXTRA_PEER_ID, callOffer.getPeerId());
    //No longer in payload.
    List<String> members = callOffer.getCallMembers();
    intent.putExtra(WebRtcCallService.EXTRA_CALL_MEMBERS, members.toArray(new String[members.size()]));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
    else                                                context.startService(intent);
  }

  private void handleCallIceCandidates(RelayContent relayContent) {
    RelayContent.ForstaCall iceUpdate = relayContent.getCall();
    ArrayList<String> sdps = new ArrayList<>();
    ArrayList<String> sdpMids = new ArrayList<>();
    ArrayList<Integer> sdpMLineIndexes = new ArrayList<>();
    for (IceCandidate ice : iceUpdate.getIceCandidates()) {
      sdps.add(ice.sdp);
      sdpMids.add(ice.sdpMid);
      sdpMLineIndexes.add(ice.sdpMLineIndex);
    }

    Intent iceIntent = new Intent(context, WebRtcCallService.class);
    iceIntent.setAction(WebRtcCallService.ACTION_ICE_MESSAGE);
    iceIntent.putExtra(WebRtcCallService.EXTRA_CALL_ID, iceUpdate.getCallId());
    iceIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, relayContent.getSenderId());
    iceIntent.putExtra(WebRtcCallService.EXTRA_DEVICE_ID, relayContent.getDeviceId());
    iceIntent.putExtra(WebRtcCallService.EXTRA_PEER_ID, iceUpdate.getPeerId());
    iceIntent.putStringArrayListExtra(WebRtcCallService.EXTRA_ICE_SDP_LIST, sdps);
    iceIntent.putStringArrayListExtra(WebRtcCallService.EXTRA_ICE_SDP_MID_LIST, sdpMids);
    iceIntent.putIntegerArrayListExtra(WebRtcCallService.EXTRA_ICE_SDP_LINE_INDEX_LIST, sdpMLineIndexes);

    context.startService(iceIntent);
  }

  private void handleCallAcceptOffer(RelayContent relayContent) {
    RelayContent.ForstaCall callAcceptOffer = relayContent.getCall();
    Intent acceptIntent = new Intent(context, WebRtcCallService.class);
    acceptIntent.setAction(WebRtcCallService.ACTION_RESPONSE_MESSAGE);
    acceptIntent.putExtra(WebRtcCallService.EXTRA_CALL_ID, callAcceptOffer.getCallId());
    acceptIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, relayContent.getSenderId());
    acceptIntent.putExtra(WebRtcCallService.EXTRA_DEVICE_ID, relayContent.getDeviceId());
    acceptIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, callAcceptOffer.getOffer());

    context.startService(acceptIntent);
  }

  private void handleCallLeave(RelayContent relayContent) {
    RelayContent.ForstaCall callLeave = relayContent.getCall();
    Intent leaveIntent = new Intent(context, WebRtcCallService.class);
    leaveIntent.setAction(WebRtcCallService.ACTION_REMOTE_HANGUP);
    leaveIntent.putExtra(WebRtcCallService.EXTRA_CALL_ID, callLeave.getCallId());
    leaveIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, relayContent.getSenderId());
    leaveIntent.putExtra(WebRtcCallService.EXTRA_DEVICE_ID, relayContent.getDeviceId());

    context.startService(leaveIntent);
  }

  private void handleSyncControlMessage(RelayContent relayContent) {
    handleControlMessage(relayContent);
  }
}
