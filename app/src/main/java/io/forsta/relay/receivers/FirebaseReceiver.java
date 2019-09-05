package io.forsta.relay.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FirebaseReceiver extends BroadcastReceiver {
  private static final String TAG = FirebaseReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w(TAG, "onReceive");
  }
}
