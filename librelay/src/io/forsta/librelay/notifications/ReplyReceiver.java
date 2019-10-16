package io.forsta.librelay.notifications;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.forsta.librelay.BuildConfig;

public class ReplyReceiver extends BroadcastReceiver {
  private static final String TAG = ReplyReceiver.class.getSimpleName();

  public static final  String REPLY_ACTION = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".notifications.REPLY";
  public static final  String THREAD_ID = "thread_id";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w(TAG, "onReceive");
  }


  public static PendingIntent getReplyIntent(Context context, long threadId) {
    Intent intent = new Intent(ReplyReceiver.REPLY_ACTION);
    intent.putExtra(ReplyReceiver.THREAD_ID, threadId);
    intent.setPackage(context.getPackageName());
    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
