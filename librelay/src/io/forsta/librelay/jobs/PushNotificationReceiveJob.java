package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.util.TextSecurePreferences;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class PushNotificationReceiveJob extends PushReceivedJob {

  private static final String TAG = PushNotificationReceiveJob.class.getSimpleName();

  SignalServiceMessageReceiver receiver;

  public PushNotificationReceiveJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public PushNotificationReceiveJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withGroupId("__notification_received")
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  protected void initialize(@NonNull SafeData data) {
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onRun() throws IOException {
    SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    pullAndProcessMessages(receiver, TAG, System.currentTimeMillis());
  }

  public void pullAndProcessMessages(SignalServiceMessageReceiver receiver, String tag, long startTime) throws IOException {
    synchronized (PushReceivedJob.RECEIVE_LOCK) {
      receiver.retrieveMessages(envelope -> {
        Log.i(tag, "Retrieved an envelope." + timeSuffix(startTime));
        handle(envelope);
        Log.i(tag, "Successfully processed an envelope." + timeSuffix(startTime));
      });
      TextSecurePreferences.setNeedsMessagePull(context, false);
    }
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "***** Failed to download pending message!");
  }

  private static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }
}
