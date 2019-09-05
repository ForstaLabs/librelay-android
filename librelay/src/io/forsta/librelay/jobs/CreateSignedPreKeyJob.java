package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.crypto.IdentityKeyUtil;
import io.forsta.librelay.crypto.PreKeyUtil;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class CreateSignedPreKeyJob extends ContextJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = CreateSignedPreKeyJob.class.getSimpleName();

  public CreateSignedPreKeyJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public CreateSignedPreKeyJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withGroupId(CreateSignedPreKeyJob.class.getSimpleName())
                                .create());
  }

  @Override
  public void onAdded() {}

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {

  }

  @Override
  public void onRun() throws IOException {
    if (TextSecurePreferences.isSignedPreKeyRegistered(context)) {
      Log.w(TAG, "Signed prekey already registered...");
      return;
    }

    IdentityKeyPair    identityKeyPair    = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, identityKeyPair);

    ApplicationDependencies.getSignalServiceAccountManager().setSignedPreKey(signedPreKeyRecord);
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }
}
