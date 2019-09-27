package io.forsta.librelay.gcm;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;

import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.jobmanager.requirements.NetworkRequirement;
import io.forsta.librelay.jobs.FcmRefreshJob;
import io.forsta.librelay.jobs.PushNotificationReceiveJob;
import io.forsta.librelay.util.PowerManagerCompat;
import io.forsta.librelay.util.ServiceUtil;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.WakeLockUtil;

public class FcmService extends FirebaseMessagingService {

  private static final String TAG = FcmService.class.getSimpleName();
  private static final String   WAKE_LOCK_TAG  = "FcmMessageProcessing";

  private static int activeCount;

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.d(TAG, "Received FCM message");
    WakeLockUtil.runWithLock(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK, 60000, WAKE_LOCK_TAG, () -> {
      handleReceivedNotification(getApplicationContext());
    });
  }

  @Override
  public void onNewToken(String token) {
    Log.w(TAG, "onNewToken()");

    if (!TextSecurePreferences.isPushRegistered(getApplicationContext())) {
      Log.w(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    ApplicationContext.getInstance(getApplicationContext())
        .getJobManager()
        .add(new FcmRefreshJob(getApplicationContext()));
  }

  private void handleReceivedNotification(Context context) {
    if (!incrementActiveGcmCount()) {
      Log.w(TAG, "Skipping processing -- there's already one enqueued.");
      return;
    }

    TextSecurePreferences.setNeedsMessagePull(context, true);

    long         startTime    = System.currentTimeMillis();
    PowerManager powerManager = ServiceUtil.getPowerManager(getApplicationContext());
    SignalServiceMessageReceiver messageReceiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    boolean      doze         = PowerManagerCompat.isDeviceIdleMode(powerManager);
    boolean      network      = new NetworkRequirement(context).isPresent();

    if (doze || !network) {
      Log.w(TAG, "We may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
    }

    try {
      new PushNotificationReceiveJob(context).pullAndProcessMessages(messageReceiver, TAG, startTime);
    } catch (IOException e) {
      if (Build.VERSION.SDK_INT >= 26) {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on the system JobScheduler (API " + Build.VERSION.SDK_INT + ").", e);
        FcmJobService.schedule(context);
      } else {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on JobManager (API " + Build.VERSION.SDK_INT + ").", e);
        ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new PushNotificationReceiveJob(context));
      }
    }

    decrementActiveGcmCount();
  }

  private static synchronized boolean incrementActiveGcmCount() {
    if (activeCount < 2) {
      activeCount++;
      return true;
    }
    return false;
  }

  private static synchronized void decrementActiveGcmCount() {
    activeCount--;
  }
}
