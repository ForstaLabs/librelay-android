package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;

public class PushContentReceiveJob extends PushReceivedJob {

  private static final String TAG = PushContentReceiveJob.class.getSimpleName();

  public PushContentReceiveJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public PushContentReceiveJob(Context context) {
    super(context, JobParameters.newBuilder().create());
  }

  @Override
  public void onAdded() {}

  @Override
  protected void initialize(@NonNull SafeData data) { }

  @Override
  public void onRun() throws Exception {
    Log.w(TAG, "This job should not be run! This is a wrapper because PushReceivedJob is abstract");
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }
}
