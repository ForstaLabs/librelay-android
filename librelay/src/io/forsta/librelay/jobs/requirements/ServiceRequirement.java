package io.forsta.librelay.jobs.requirements;

import android.content.Context;

import io.forsta.librelay.jobmanager.dependencies.ContextDependent;
import io.forsta.librelay.jobmanager.requirements.SimpleRequirement;

public class ServiceRequirement extends SimpleRequirement implements ContextDependent {

  private static final String TAG = ServiceRequirement.class.getSimpleName();

  private transient Context context;

  public ServiceRequirement(Context context) {
    this.context  = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    TelephonyServiceState telephonyServiceState = new TelephonyServiceState();
    return telephonyServiceState.isConnected(context);
  }
}
