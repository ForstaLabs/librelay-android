package io.forsta.librelay.notifications;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.forsta.librelay.BuildConfig;

public class NotificationUpdateReceiver extends BroadcastReceiver {
  private static final String TAG = NotificationUpdateReceiver.class.getSimpleName();

  public static final  String UPDATE_ACTION = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".notifications.UPDATE";
  public static final  String THREAD_ID = "thread_id";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w(TAG, "Notification update received");
    long threadId = intent.getLongExtra(THREAD_ID, -1);
    if (threadId > 0) {
      MessageNotifier.updateNotification(context, threadId);
    } else {
      MessageNotifier.updateNotification(context);
    }
  }

  public static Intent getUpdateIntent(Context context, long threadId) {
    Intent intent = new Intent(UPDATE_ACTION);
    intent.putExtra(ReplyReceiver.THREAD_ID, threadId);
    intent.setPackage(context.getPackageName());
    return intent;
  }

}
