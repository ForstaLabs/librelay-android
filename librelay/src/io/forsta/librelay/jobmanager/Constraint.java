package io.forsta.librelay.jobmanager;

import android.app.job.JobInfo;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

public interface Constraint {

  boolean isMet();

  @NonNull
  String getFactoryKey();

  @RequiresApi(26)
  void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder);

  interface Factory<T extends Constraint> {
    T create();
  }
}