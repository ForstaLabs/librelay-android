package io.forsta.librelay.messaging;

import android.content.Context;

import io.forsta.librelay.service.ForstaServiceAccountManager;
import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.util.TextSecurePreferences;


public class TextSecureCommunicationFactory {

  public static ForstaServiceAccountManager createManager(Context context) {
    return new ForstaServiceAccountManager(BuildConfig.SIGNAL_API_URL,
                                           null,
                                           TextSecurePreferences.getLocalAddress(context),
                                           TextSecurePreferences.getLocalDeviceId(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           TextSecurePreferences.getUserAgent(context));
  }

  public static ForstaServiceAccountManager createManager(Context context, String addr,
                                                          String password) {
    return new ForstaServiceAccountManager(BuildConfig.SIGNAL_API_URL,
                                           null,
                                           addr,
                                           new Integer(-1),
                                           password,
                                           TextSecurePreferences.getUserAgent(context));
  }

}
