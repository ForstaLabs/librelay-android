package io.forsta.librelay.gcm;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.jobs.PushNotificationReceiveJob;
import io.forsta.librelay.util.ServiceUtil;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.ApplicationContext;


/**
 * Pulls down messages. Used when we fail to pull down messages in {@link FcmService}.
 */
@RequiresApi(26)
public class FcmJobService extends JobService {

  private static final String TAG = FcmJobService.class.getSimpleName();

  private static final Executor MESSAGE_EXECUTOR = newCachedSingleThreadExecutor("FcmMessageProcessing");

  private static final int ID = 1337;

  @RequiresApi(26)
  public static void schedule(@NonNull Context context) {
    JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(ID, new ComponentName(context, FcmJobService.class))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_LINEAR)
        .setPersisted(true);

    ServiceUtil.getJobScheduler(context).schedule(jobInfoBuilder.build());
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Log.w(TAG, "FCM JobService onStartJob()");

    if (ApplicationContext.getInstance(getApplicationContext()).isAppVisible()) {
      Log.i(TAG, "App is foregrounded. No need to run.");
      return false;
    }

    MESSAGE_EXECUTOR.execute(() -> {
      SignalServiceMessageReceiver messageReceiver = ApplicationDependencies.getSignalServiceMessageReceiver();
      try {
        new PushNotificationReceiveJob(getApplicationContext()).pullAndProcessMessages(messageReceiver, TAG, System.currentTimeMillis());
        Log.i(TAG, "Successfully retrieved messages.");
        jobFinished(params, false);
      } catch (IOException e) {
        Log.w(TAG, "Failed to pull. Scheduling a retry.", e);
        jobFinished(params, true);
      }
    });

    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Log.d(TAG, "onStopJob()");
    return TextSecurePreferences.getNeedsMessagePull(getApplicationContext());
  }

  public static ExecutorService newCachedSingleThreadExecutor(final String name) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, name));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }
}