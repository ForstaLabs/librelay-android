package io.forsta.librelay.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.R;
import io.forsta.librelay.jobmanager.requirements.NetworkRequirement;
import io.forsta.librelay.jobmanager.requirements.NetworkRequirementProvider;
import io.forsta.librelay.jobmanager.requirements.RequirementListener;
import io.forsta.librelay.jobs.PushContentReceiveJob;
import io.forsta.librelay.notifications.NotificationChannels;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageRetrievalService extends Service implements RequirementListener {

  private static final String TAG = MessageRetrievalService.class.getSimpleName();
  public static final String RESTART_ACTION = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".RESTART";

  public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
  public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";
  public static final  String ACTION_INITIALIZE        = "INITIALIZE";
  public static final  int    FOREGROUND_ID            = 313399;

  private static final long   REQUEST_TIMEOUT_MINUTES  = 1;

  private NetworkRequirement networkRequirement;
  private NetworkRequirementProvider networkRequirementProvider;

  public SignalServiceMessageReceiver receiver;

  private int          activeActivities = 0;
  private List<Intent> pushPending      = new LinkedList<>();
  private MessageRetrievalThread retrievalThread  = null;

  public static SignalServiceMessagePipe pipe = null;

  @Override
  public void onCreate() {
    super.onCreate();
    receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    networkRequirement         = new NetworkRequirement(this);
    networkRequirementProvider = new NetworkRequirementProvider(this);

    networkRequirementProvider.setListener(this);

    retrievalThread = new MessageRetrievalThread();
    retrievalThread.start();

    setForegroundIfNecessary();
    Log.w(TAG, "Restart action: " + RESTART_ACTION);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_STICKY;

    if      (ACTION_ACTIVITY_STARTED.equals(intent.getAction()))  incrementActive();
    else if (ACTION_ACTIVITY_FINISHED.equals(intent.getAction())) decrementActive();

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    if (retrievalThread != null) {
      retrievalThread.stopThread();
    }

    sendBroadcast(new Intent(RESTART_ACTION));
  }


  @Override
  public void onRequirementStatusChanged() {
    synchronized (this) {
      notifyAll();
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void setForegroundIfNecessary() {
    if (TextSecurePreferences.isFcmDisabled(this)) {
      NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.OTHER);
      builder.setContentTitle(getString(R.string.MessageRetrievalService_forsta));
      builder.setContentText(getString(R.string.MessageRetrievalService_background_connection_enabled));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setWhen(0);
      builder.setSmallIcon(R.drawable.icon_notification);
      startForeground(FOREGROUND_ID, builder.build());
    }
  }

  private synchronized void incrementActive() {
    activeActivities++;
    Log.w(TAG, "Active Count: " + activeActivities);
    notifyAll();
  }

  private synchronized void decrementActive() {
    activeActivities--;
    Log.w(TAG, "Active Count: " + activeActivities);
    notifyAll();
  }

  private synchronized boolean isConnectionNecessary() {
    Log.w(TAG, String.format("Network requirement: %s, active activities: %s, push pending: %s",
                             networkRequirement.isPresent(), activeActivities, pushPending.size()));

    return TextSecurePreferences.isWebsocketRegistered(this) &&
           (activeActivities > 0 || !pushPending.isEmpty())  &&
           networkRequirement.isPresent();
  }

  private synchronized void waitForConnectionNecessary() {
    try {
      while (!isConnectionNecessary()) wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private void shutdown(SignalServiceMessagePipe pipe) {
    try {
      pipe.shutdown();
    } catch (Throwable t) {
      Log.w(TAG, t);
    }
  }

  public static void registerActivityStarted(Context activity) {
    Intent intent = new Intent(activity, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_ACTIVITY_STARTED);
    activity.startService(intent);
  }

  public static void registerActivityStopped(Context activity) {
    Intent intent = new Intent(activity, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_ACTIVITY_FINISHED);
    activity.startService(intent);
  }

  public static @Nullable
  SignalServiceMessagePipe getPipe() {
    return pipe;
  }

  private class MessageRetrievalThread extends Thread implements Thread.UncaughtExceptionHandler {

    private AtomicBoolean stopThread = new AtomicBoolean(false);

    MessageRetrievalThread() {
      super("MessageRetrievalService");
      setUncaughtExceptionHandler(this);
    }

    @Override
    public void run() {
      while (!stopThread.get()) {
        Log.i(TAG, "Waiting for websocket state change....");
        waitForConnectionNecessary();

        Log.i(TAG, "Making websocket connection....");
        pipe = receiver.createMessagePipe();

        SignalServiceMessagePipe localPipe = pipe;

        try {
          while (isConnectionNecessary() && !stopThread.get()) {
            try {
              Log.i(TAG, "Reading message...");
              localPipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                  envelope -> {
                    Log.i(TAG, "Retrieved envelope! " + envelope.getSource());
                    PushContentReceiveJob receiveJob = new PushContentReceiveJob(MessageRetrievalService.this);
                    receiveJob.handle(envelope);
                  });
            } catch (TimeoutException e) {
              Log.w(TAG, "Application level read timeout...");
            } catch (InvalidVersionException e) {
              Log.w(TAG, e);
            }
          }
        } catch (Throwable e) {
          Log.w(TAG, e);
        } finally {
          Log.w(TAG, "Shutting down pipe...");
          shutdown(localPipe);
        }

        Log.i(TAG, "Looping...");
      }

      Log.i(TAG, "Exiting...");
    }

    private void stopThread() {
      stopThread.set(true);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      Log.w(TAG, "*** Uncaught exception!");
      Log.w(TAG, e);
    }
  }
}
