package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.crypto.IdentityKeyUtil;
import io.forsta.librelay.crypto.PreKeyUtil;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.List;

public class RefreshPreKeysJob extends ContextJob {

  private static final String TAG = RefreshPreKeysJob.class.getSimpleName();

  private static final int PREKEY_MINIMUM = 10;

  public RefreshPreKeysJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public RefreshPreKeysJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(RefreshPreKeysJob.class.getSimpleName())
                                .withNetworkRequirement()
                                .withRetryCount(5)
                                .create());
  }

  @Override
  public void onAdded() {

  }

  @Override
  protected void initialize(@NonNull SafeData data) {
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }


  @Override
  public void onRun() throws IOException {
    if (!TextSecurePreferences.isPushRegistered(context)) return;

    int availableKeys = ApplicationDependencies.getSignalServiceAccountManager().getPreKeysCount();

    if (availableKeys >= PREKEY_MINIMUM && TextSecurePreferences.isSignedPreKeyRegistered(context)) {
      Log.w(TAG, "Available keys sufficient: " + availableKeys);
      return;
    }

    List<PreKeyRecord> preKeyRecords       = PreKeyUtil.generatePreKeys(context);
    PreKeyRecord       lastResortKeyRecord = PreKeyUtil.generateLastResortKey(context);
    IdentityKeyPair    identityKey         = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord  = PreKeyUtil.generateSignedPreKey(context, identityKey);

    Log.w(TAG, "Registering new prekeys...");

    ApplicationDependencies.getSignalServiceAccountManager().setPreKeys(identityKey.getPublicKey(), lastResortKeyRecord, signedPreKeyRecord, preKeyRecords);

    TextSecurePreferences.setSignedPreKeyRegistered(context, true);

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new CleanPreKeysJob(context));
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof NonSuccessfulResponseCodeException) return false;
    if (exception instanceof PushNetworkException)               return true;

    return false;
  }

  @Override
  public void onCanceled() {

  }

}
