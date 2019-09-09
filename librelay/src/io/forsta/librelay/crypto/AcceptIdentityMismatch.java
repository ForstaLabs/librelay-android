package io.forsta.librelay.crypto;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.Identities;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.database.MessageReceiptsDatabase;
import io.forsta.librelay.database.PushDatabase;
import io.forsta.librelay.database.documents.IdentityKeyMismatch;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.jobs.IdentityUpdateJob;
import io.forsta.librelay.jobs.PushDecryptJob;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.messaging.MessageSender;
import io.forsta.librelay.util.Base64;

/**
 * Created by jlewis on 7/19/17.
 */

public class AcceptIdentityMismatch extends AsyncTask<Void, Void, Void> {
  private final Context context;
  private final MessageRecord messageRecord;
  private final IdentityKeyMismatch mismatch;

  public AcceptIdentityMismatch(Context context, MessageRecord messageRecord, IdentityKeyMismatch mismatch) {
    this.context = context;
    this.messageRecord = messageRecord;
    this.mismatch      = mismatch;
  }

  @Override
  protected Void doInBackground(Void... voids) {
    Identities identityDatabase = DbFactory.getIdentities(context);

    String address = DbFactory.getContacts(context).getAddressFromId(mismatch.getRecipientId());
    identityDatabase.saveIdentity(address,
        mismatch.getIdentityKey());

    processMessageRecord(messageRecord);
    processPendingMessageRecords(messageRecord.getThreadId(), mismatch);

    ApplicationContext.getInstance(context)
        .getJobManager()
        .add(new IdentityUpdateJob(context, mismatch.getRecipientId()));

    return null;
  }

  private void processMessageRecord(MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord);
    else                            processIncomingMessageRecord(messageRecord);
  }

  private void processPendingMessageRecords(long threadId, IdentityKeyMismatch mismatch) {
    MessageDatabase messageDatabase = DbFactory.getMessageDatabase(context);
    Cursor cursor         = messageDatabase.getIdentityConflictMessagesForThread(threadId);
    MessageDatabase.Reader reader         = messageDatabase.readerFor(cursor);
    MessageRecord         record;

    try {
      while ((record = reader.getNext()) != null) {
        for (IdentityKeyMismatch recordMismatch : record.getIdentityKeyMismatches()) {
          if (mismatch.equals(recordMismatch)) {
            processMessageRecord(record);
          }
        }
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private void processOutgoingMessageRecord(MessageRecord messageRecord) {
    MessageDatabase messageDatabase = DbFactory.getMessageDatabase(context);
    MessageReceiptsDatabase messageReceiptsDatabase = DbFactory.getMessageReceiptDatabase(context);

    messageDatabase.removeMismatchedIdentity(messageRecord.getId(),
        mismatch.getRecipientId(),
        mismatch.getIdentityKey());

    Recipients recipients = messageReceiptsDatabase.getRecipientsForId(messageRecord.getId());

    MessageSender.resend(context, messageRecord);
  }

  private void processIncomingMessageRecord(MessageRecord messageRecord) {
    try {
      PushDatabase pushDatabase = DbFactory.getPushDatabase(context);

      SignalServiceEnvelope envelope = new SignalServiceEnvelope(SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
          messageRecord.getIndividualRecipient().getAddress(),
          messageRecord.getRecipientDeviceId(), "",
          messageRecord.getDateSent(),
          Base64.decode(messageRecord.getBody()),
          null);

      long pushId = pushDatabase.insert(envelope);

      ApplicationContext.getInstance(context)
          .getJobManager()
          .add(new PushDecryptJob(context, pushId));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
