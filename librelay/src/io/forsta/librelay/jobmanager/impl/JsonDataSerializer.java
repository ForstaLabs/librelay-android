package io.forsta.librelay.jobmanager.impl;

import android.support.annotation.NonNull;
import android.util.Log;

import io.forsta.librelay.jobmanager.Data;
import io.forsta.librelay.util.JsonUtils;

import java.io.IOException;

public class JsonDataSerializer implements Data.Serializer {

  private static final String TAG = JsonDataSerializer.class.getSimpleName();

  @Override
  public @NonNull
  String serialize(@NonNull Data data) {
    try {
      return JsonUtils.toJson(data);
    } catch (IOException e) {
      Log.e(TAG, "Failed to serialize to JSON.", e);
      throw new AssertionError(e);
    }
  }

  @Override
  public @NonNull Data deserialize(@NonNull String serialized) {
    try {
      return JsonUtils.fromJson(serialized, Data.class);
    } catch (IOException e) {
      Log.e(TAG, "Failed to deserialize JSON.", e);
      throw new AssertionError(e);
    }
  }
}
