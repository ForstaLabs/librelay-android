package io.forsta.relay.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationReplyReceiver extends BroadcastReceiver {
  private static final String TAG = NotificationReplyReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "notification reply intent received for threadId: " + intent.getLongExtra("thread_id", -1));
  }
}
