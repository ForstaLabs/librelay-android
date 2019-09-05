package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.WorkerParameters;
import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.database.MessageDatabase.SyncMessageId;
import io.forsta.librelay.jobmanager.JobManager;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.recipients.RecipientFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public abstract class PushReceivedJob extends ContextJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  public static final Object RECEIVE_LOCK = new Object();

  protected PushReceivedJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected PushReceivedJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  public void handle(SignalServiceEnvelope envelope) {
    synchronized (RECEIVE_LOCK) {
      if (envelope.isReceipt()) {
        Log.w(TAG, "Received delivery receipt" + envelope.getSource() + ":" + envelope.getSourceDevice());
        handleReceipt(envelope);
      } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage()) {
        Log.w(TAG, "Received message from " + envelope.getSource() + ":" + envelope.getSourceDevice());
        if (envelope.isPreKeySignalMessage()) {
          Log.w(TAG, "PreKeySignalMessage");
        }
        handleMessage(envelope);
      } else {
        Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
      }
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope) {
    Recipient sender = RecipientFactory.getRecipient(context, envelope.getSource(), false);
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    if (!sender.isBlocked()) {
      long messageId = DbFactory.getPushDatabase(context).insert(envelope);
      jobManager.add(new PushDecryptJob(context, messageId));
    } else {
      Log.w(TAG, "*** Received blocked push message, ignoring...");
    }
  }

  private void handleReceipt(SignalServiceEnvelope envelope) {
    DbFactory.getMessageDatabase(context).incrementDeliveryReceiptCount(new SyncMessageId(envelope.getSource(),
                                                                                               envelope.getTimestamp()));
  }
}
