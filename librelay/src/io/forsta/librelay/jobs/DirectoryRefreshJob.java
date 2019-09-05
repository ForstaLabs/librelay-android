package io.forsta.librelay.jobs;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.recipients.DirectoryHelper;

import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends ContextJob {
  private static String TAG = DirectoryRefreshJob.class.getSimpleName();

  @Nullable private transient Recipients recipients;

  public DirectoryRefreshJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public DirectoryRefreshJob(@NonNull Context context) {
    super(context, JobParameters.newBuilder()
        .withGroupId(DirectoryRefreshJob.class.getSimpleName())
        .withNetworkRequirement()
        .create());
  }

  public DirectoryRefreshJob(@NonNull Context context,
                             @NonNull Recipients recipients)
  {
    super(context, JobParameters.newBuilder()
                                .withGroupId(DirectoryRefreshJob.class.getSimpleName())
                                .withNetworkRequirement()
                                .create());

    this.recipients   = recipients;
  }

  @Override
  public void onAdded() {}

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {

  }

  @Override
  public void onRun() throws IOException {
    Log.w(TAG, "DirectoryRefreshJob.onRun()");
    PowerManager          powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Directory Refresh");

    try {
      wakeLock.acquire();
      if (recipients == null) {
        DirectoryHelper.refreshDirectory(context);
      } else {
        DirectoryHelper.refreshDirectoryFor(context, recipients);
      }
    } finally {
      if (wakeLock.isHeld()) wakeLock.release();
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {}
}
