package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;

public class IdentityUpdateJob extends ContextJob {

  private static final String KEY_RECIPIENT_ID = "recipient_id";

  private long recipientId;

  public IdentityUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public IdentityUpdateJob(Context context, long recipientId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(IdentityUpdateJob.class.getName())
                                .create());
    this.recipientId = recipientId;
  }

  @Override
  public void onRun() throws Exception {
    Recipient            recipient      = RecipientFactory.getRecipientForId(context, recipientId, true);
    Recipients           recipients     = RecipientFactory.getRecipientsFor(context, recipient, true);
    String               number         = recipient.getAddress();
    long                 time           = System.currentTimeMillis();
    MessageDatabase database = DbFactory.getMessageDatabase(context);
    ThreadDatabase       threadDatabase = DbFactory.getThreadDatabase(context);

    //TODO insert identity update message into messages table.
//    if (threadDatabase.getThreadIdIfExistsFor(recipients) != -1) {
//      IncomingTextMessage           incoming         = new IncomingTextMessage(number, 1, time, null, Optional.<SignalServiceGroup>absent(), 0);
//      IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
//      database.insertMessageInbox(individualUpdate);
//    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onAdded() {

  }

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_RECIPIENT_ID, recipientId).build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    recipientId = data.getLong(KEY_RECIPIENT_ID);
  }

  @Override
  public void onCanceled() {

  }
}
