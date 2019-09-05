package io.forsta.librelay.crypto;

import android.support.annotation.NonNull;

import io.forsta.librelay.util.Base64;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;

public class MediaKey {

  public static String getEncoded(@NonNull byte[] key) {
    return Base64.encodeBytes(key);
  }

  public static byte[] getDecoded(@NonNull String encodedKey)
      throws IOException
  {
    return Base64.decode(encodedKey);
  }
}
