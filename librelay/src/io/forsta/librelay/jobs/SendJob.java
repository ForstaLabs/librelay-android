package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.WorkerParameters;
import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.AttachmentDatabase;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.media.MediaConstraints;
import io.forsta.librelay.media.MediaStream;
import io.forsta.librelay.transport.UndeliverableMessageException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.MmsException;

public abstract class SendJob extends ContextJob {

  private final static String TAG = SendJob.class.getSimpleName();

  protected SendJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public SendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  @Override
  public final void onRun() throws Exception {
    onSend();
  }

  protected abstract void onSend() throws Exception;

  protected void markAttachmentsUploaded(long messageId, @NonNull List<Attachment> attachments) {
    AttachmentDatabase database = DbFactory.getAttachmentDatabase(context);

    for (Attachment attachment : attachments) {
      database.markAttachmentUploaded(messageId, attachment);
    }
  }

  protected List<Attachment> scaleAttachments(@NonNull MediaConstraints constraints,
                                              @NonNull List<Attachment> attachments)
      throws UndeliverableMessageException
  {
    AttachmentDatabase attachmentDatabase = DbFactory.getAttachmentDatabase(context);
    List<Attachment>   results            = new LinkedList<>();

    for (Attachment attachment : attachments) {
      try {
        if (constraints.isSatisfied(context, attachment)) {
          results.add(attachment);
        } else if (constraints.canResize(attachment)) {
          MediaStream resized = constraints.getResizedMedia(context, attachment);
          results.add(attachmentDatabase.updateAttachmentData(attachment, resized));
        } else {
          throw new UndeliverableMessageException("Size constraints could not be met!");
        }
      } catch (IOException | MmsException e) {
        throw new UndeliverableMessageException(e);
      }
    }

    return results;
  }
}
