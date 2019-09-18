package io.forsta.librelay.jobmanager.requirements;

import android.support.annotation.NonNull;

import io.forsta.librelay.jobmanager.Job;

public abstract class SimpleRequirement implements Requirement {

  @Override
  public boolean isPresent(@NonNull Job job) {
    return isPresent();
  }

  @Override
  public void onRetry(@NonNull Job job) {
  }

  public abstract boolean isPresent();
}
