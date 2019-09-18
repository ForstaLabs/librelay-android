package io.forsta.librelay.jobmanager.impl;

import android.support.annotation.NonNull;

import io.forsta.librelay.jobmanager.ExecutorFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultExecutorFactory implements ExecutorFactory {
  @Override
  public @NonNull
  ExecutorService newSingleThreadExecutor(@NonNull String name) {
    return Executors.newSingleThreadExecutor(r -> new Thread(r, name));
  }
}
