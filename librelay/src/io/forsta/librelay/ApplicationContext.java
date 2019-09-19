/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.librelay;

import android.app.ActivityManager;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.util.HashSet;
import java.util.Set;

import androidx.work.WorkManager;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.dependencies.ApplicationDependencyProvider;
import io.forsta.librelay.jobmanager.JobManager;
import io.forsta.librelay.jobs.CreateSignedPreKeyJob;
import io.forsta.librelay.jobs.FcmRefreshJob;
import io.forsta.librelay.jobs.PushNotificationReceiveJob;
import io.forsta.librelay.jobs.requirements.MediaNetworkRequirementProvider;
import io.forsta.librelay.notifications.NotificationChannels;
import io.forsta.librelay.service.ExpiringMessageManager;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getName();

  private ExpiringMessageManager expiringMessageManager;
  private JobManager jobManager;
  private boolean                initialized;
  private volatile boolean       isAppVisible;


  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    synchronized(this) {
      super.onCreate();
      initializeLogging();
      initializeDependencies();
      initializeJobManager();
      initializeExpiringMessageManager();
      initializeGcmCheck();
      initializeSignedPreKeyCheck();
      initializeWebRtc();
      initializePendingMessages();
      NotificationChannels.create(this);
      ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
      initialized = true;
      notifyAll();
    }
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
  }

  public void ensureInitialized() {
    synchronized (this) {
      while (!initialized) {
        Util.wait(this, 0);
      }
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeLogging() {
    System.out.println("XXX: Too lazy to port logging provider stuff from android build of libsignal-protocol");
    //SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
  }

  private void initializeJobManager() {
    this.jobManager = new JobManager(this, WorkManager.getInstance());
  }

  private void initializeDependencies() {
    ApplicationDependencies.init(new ApplicationDependencyProvider(this));
  }

  private void initializeGcmCheck() {
    Log.w(TAG, "initializing FCM");
    if (TextSecurePreferences.isPushRegistered(this)) {
      String token = TextSecurePreferences.getFcmToken(this);
      Log.w(TAG, "Local FCM token: " + token);
      if (token == null) {
        this.jobManager.add(new FcmRefreshJob(this));
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (TextSecurePreferences.isPushRegistered(this) &&
        !TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
          .createInitializationOptions());

    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  public void clearApplicationData() {
    ((ActivityManager) getSystemService(ACTIVITY_SERVICE))
        .clearApplicationUserData();
  }
}
