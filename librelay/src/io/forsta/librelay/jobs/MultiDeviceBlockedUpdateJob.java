package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class MultiDeviceBlockedUpdateJob extends ContextJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceBlockedUpdateJob.class.getSimpleName();

  public MultiDeviceBlockedUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MultiDeviceBlockedUpdateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withGroupId(MultiDeviceBlockedUpdateJob.class.getSimpleName())
                                .create());
  }

  @Override
  public void onRun()
      throws IOException, UntrustedIdentityException
  {
    // TODO Send blocked control message.
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onAdded() {

  }

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {

  }

  @Override
  public void onCanceled() {

  }
}
