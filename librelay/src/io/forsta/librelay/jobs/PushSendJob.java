package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.WorkerParameters;
import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.events.PartProgressEvent;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.media.PartAuthority;
import io.forsta.librelay.notifications.MessageNotifier;
import io.forsta.librelay.recipients.Recipients;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.greenrobot.eventbus.EventBus;

public abstract class PushSendJob extends SendJob {

  private static final String TAG = PushSendJob.class.getSimpleName();

  protected  PushSendJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected PushSendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected static JobParameters constructParameters(Context context, String destination) {
    JobParameters.Builder builder = JobParameters.newBuilder();
    builder.withGroupId(destination);
    builder.withNetworkRequirement();
    builder.withRetryCount(5);

    return builder.create();
  }

  protected SignalServiceAddress getPushAddress(String address) {
    return new SignalServiceAddress(address, Optional.fromNullable(null));
  }

  protected List<SignalServiceAttachment> getAttachmentsFor(List<Attachment> parts) {
    List<SignalServiceAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {

      try {
        if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
        InputStream is = PartAuthority.getAttachmentStream(context, attachment.getDataUri());
        attachments.add(SignalServiceAttachment.newStreamBuilder()
            .withStream(is)
            .withContentType(attachment.getContentType())
            .withLength(attachment.getSize())
            .withListener(new ProgressListener() {
              @Override
              public void onAttachmentProgress(long total, long progress) {
                EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress));
              }
            })
            .build());
      } catch (IOException ioe) {
        Log.w(TAG, "Couldn't open attachment", ioe);
      }
    }

    return attachments;
  }

  protected void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DbFactory.getMessageDatabase(context).getThreadIdForMessage(messageId);

    if (threadId != -1) {
      MessageNotifier.notifyMessageDeliveryFailed(context, threadId);
    }
  }
}
