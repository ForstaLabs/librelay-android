package io.forsta.librelay.service;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.jobs.DirectoryRefreshJob;
import io.forsta.librelay.util.TextSecurePreferences;

public class DirectoryRefreshListener extends BroadcastReceiver {

  private static final String TAG = DirectoryRefreshListener.class.getSimpleName();

  private static final String REFRESH_EVENT = "io.forsta.librelay.DIRECTORY_REFRESH";
  private static final String BOOT_EVENT    = "android.intent.action.BOOT_COMPLETED";

  private static final long   INTERVAL      = 12 * 60 * 60 * 1000; // 12 hours.

  @Override
  public void onReceive(Context context, Intent intent) {
    if      (REFRESH_EVENT.equals(intent.getAction())) handleRefreshAction(context);
    else if (BOOT_EVENT.equals(intent.getAction()))    handleBootEvent(context);
  }

  private void handleBootEvent(Context context) {
    schedule(context);
  }

  private void handleRefreshAction(Context context) {
    schedule(context);
  }

  public static void schedule(Context context) {
    if (!TextSecurePreferences.isPushRegistered(context)) return;

    AlarmManager      alarmManager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    Intent            intent        = new Intent(DirectoryRefreshListener.REFRESH_EVENT);
    PendingIntent     pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    long              time          = TextSecurePreferences.getDirectoryRefreshTime(context);

    if (time <= System.currentTimeMillis()) {
      if (time != 0) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new DirectoryRefreshJob(context));
      }

      time = System.currentTimeMillis() + INTERVAL;
    }

    Log.w(TAG, "Scheduling for: " + time);

    alarmManager.cancel(pendingIntent);
    alarmManager.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);

    TextSecurePreferences.setDirectoryRefreshTime(context, time);
  }

}
