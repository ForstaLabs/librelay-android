package io.forsta.librelay.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.forsta.librelay.BuildConfig;

public class ReplyReceiver extends BroadcastReceiver {
  private static final String TAG = ReplyReceiver.class.getSimpleName();

  public static final  String REPLY_ACTION     = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".notifications.REPLY";
  public static final  String THREAD_ID = "thread_id";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w(TAG, "onReceive");
  }
}
