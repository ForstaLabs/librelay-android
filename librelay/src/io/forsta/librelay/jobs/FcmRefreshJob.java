/**
 * Copyright (C) 2014 Open Whisper Systems
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
package io.forsta.librelay.jobs;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import androidx.work.WorkerParameters;
import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.gcm.FcmUtil;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.transport.RetryLaterException;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.jobmanager.JobParameters;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;
import androidx.work.Data;


public class FcmRefreshJob extends ContextJob {

  private static final String TAG = FcmRefreshJob.class.getSimpleName();
  private static final String FCM_EVENT = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".FCM_EVENT";

  public FcmRefreshJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public FcmRefreshJob(Context context) {
    super(context, JobParameters.newBuilder()
        .withGroupId(FcmRefreshJob.class.getSimpleName())
        .withDuplicatesIgnored(true)
        .withNetworkRequirement()
        .withRetryCount(1)
        .create());
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onRun() throws Exception {
    Log.w(TAG, "FCM Reregistering...");

    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);

    if (result != ConnectionResult.SUCCESS) {
      notifyFcmFailure();
    } else {
      Optional<String> token = FcmUtil.getToken();

      if (token.isPresent()) {
        Log.d(TAG, "New FCM token issued: " + token.get());
        ApplicationDependencies.getSignalServiceAccountManager().setGcmId(token);
        TextSecurePreferences.setFcmToken(context, token.get());
        TextSecurePreferences.setFcmTokenLastSetTime(context, System.currentTimeMillis());
        TextSecurePreferences.setWebsocketRegistered(context, true);
      } else {
        throw new RetryLaterException(new IOException("FCM Failed to retrieve a token."));
      }
    }
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "FCM reregistration failed after retry attempt exhaustion!");
  }

  @Override
  public boolean onShouldRetry(Exception throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    return true;
  }

  private void notifyFcmFailure() {
    Log.w(TAG, "FCM refresh failed!");
    Intent intent = new Intent(FCM_EVENT);
    getContext().sendBroadcast(intent);
  }
}