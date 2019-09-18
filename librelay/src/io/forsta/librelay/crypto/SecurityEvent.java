package io.forsta.librelay.crypto;

import android.content.Context;
import android.content.Intent;

import io.forsta.librelay.BuildConfig;

/**
 * This class processes key exchange interactions.
 *
 * @author Moxie Marlinspike
 */

public class SecurityEvent {

  public static final String SECURITY_UPDATE_EVENT = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".KEY_EXCHANGE_UPDATE";
  public  static final String KEY_PERMISSION = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".ACCESS_SECRETS";

  public static void broadcastSecurityUpdateEvent(Context context) {
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KEY_PERMISSION);
  }

}
