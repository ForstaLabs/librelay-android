package io.forsta.librelay.service;

import android.app.Service;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.R;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.jobmanager.ConstraintObserver;
import io.forsta.librelay.jobmanager.impl.NetworkConstraint;
import io.forsta.librelay.jobmanager.impl.NetworkConstraintObserver;
import io.forsta.librelay.jobs.PushContentReceiveJob;
import io.forsta.librelay.notifications.NotificationChannels;
import io.forsta.librelay.util.TextSecurePreferences;

public class IncomingMessageObserver implements ConstraintObserver.Notifier {

  private static final String TAG = IncomingMessageObserver.class.getSimpleName();

  public  static final  int FOREGROUND_ID            = 313399;
  private static final long REQUEST_TIMEOUT_MINUTES  = 1;

  private static SignalServiceMessagePipe pipe             = null;

  private final Context context;
  private final NetworkConstraint networkConstraint;
  private final SignalServiceMessageReceiver receiver;

  private boolean appVisible;


  public IncomingMessageObserver(@NonNull Context context) {
    this.context           = context;
    this.networkConstraint = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create();
    this.receiver          = ApplicationDependencies.getSignalServiceMessageReceiver();

    new NetworkConstraintObserver(ApplicationContext.getInstance(context)).register(this);
    new MessageRetrievalThread().start();

    if (TextSecurePreferences.isFcmDisabled(context)) {
      ContextCompat.startForegroundService(context, new Intent(context, ForegroundService.class));
    }

    ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner) {
        onAppForegrounded();
      }

      @Override
      public void onStop(@NonNull LifecycleOwner owner) {
        onAppBackgrounded();
      }
    });
  }

  @Override
  public void onConstraintMet(@NonNull String reason) {
    synchronized (this) {
      notifyAll();
    }
  }

  private synchronized void onAppForegrounded() {
    appVisible = true;
    notifyAll();
  }

  private synchronized void onAppBackgrounded() {
    appVisible = false;
    notifyAll();
  }

  private synchronized boolean isConnectionNecessary() {
    boolean isGcmDisabled = TextSecurePreferences.isFcmDisabled(context);

    Log.d(TAG, String.format("Network requirement: %s, app visible: %s, gcm disabled: %b",
        networkConstraint.isMet(), appVisible, isGcmDisabled));

    return TextSecurePreferences.isPushRegistered(context)      &&
        TextSecurePreferences.isWebsocketRegistered(context) &&
        (appVisible || isGcmDisabled)                        &&
        networkConstraint.isMet();
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

  public static @Nullable
  SignalServiceMessagePipe getPipe() {
    return pipe;
  }

  private class MessageRetrievalThread extends Thread implements Thread.UncaughtExceptionHandler {

    MessageRetrievalThread() {
      super("MessageRetrievalService");
      setUncaughtExceptionHandler(this);
    }

    @Override
    public void run() {
      while (true) {
        Log.i(TAG, "Waiting for websocket state change....");
        waitForConnectionNecessary();
        Log.i(TAG, "Making websocket connection....");

        pipe = receiver.createMessagePipe();
        SignalServiceMessagePipe localPipe = pipe;

        try {
          while (isConnectionNecessary()) {
            try {
              localPipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                  envelope -> {
                    Log.i(TAG, "Retrieved envelope! " + String.valueOf(envelope.getSource()));
                    new PushContentReceiveJob(context).handle(envelope);
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
      }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      Log.w(TAG, "*** Uncaught exception!");
      Log.w(TAG, e);
    }
  }

  public static class ForegroundService extends Service {

    @Override
    public @Nullable
    IBinder onBind(Intent intent) {
      return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      super.onStartCommand(intent, flags, startId);

      NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NotificationChannels.OTHER);
      builder.setContentTitle(getApplicationContext().getString(R.string.MessageRetrievalService_forsta));
      builder.setContentText(getApplicationContext().getString(R.string.MessageRetrievalService_background_connection_enabled));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setWhen(0);
      builder.setSmallIcon(R.drawable.ic_forsta_notification);
      startForeground(FOREGROUND_ID, builder.build());

      return Service.START_STICKY;
    }
  }
}
