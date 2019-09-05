package io.forsta.relay.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RegistrationReceiver extends BroadcastReceiver {
  private static final String TAG = RegistrationReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w(TAG, "app Registration event");
  }
}
