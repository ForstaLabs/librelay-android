package io.forsta.librelay.dependencies;

import android.content.Context;

import io.forsta.librelay.service.ForstaServiceAccountManager;
import io.forsta.librelay.crypto.storage.SignalProtocolStoreImpl;
import io.forsta.librelay.messaging.SecurityEventListener;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

public class TextSecureCommunicationFactory {

  private final Context context;

  private TextSecureCommunicationFactory(Context context) {
    this.context = context;
  }

  public static SignalServiceMessageSender createMessageSender(Context context) {
    return new SignalServiceMessageSender(TextSecurePreferences.getServer(context),
        null,
        TextSecurePreferences.getLocalAddress(context),
        TextSecurePreferences.getLocalDeviceId(context),
        TextSecurePreferences.getPushServerPassword(context),
        new SignalProtocolStoreImpl(context),
        TextSecurePreferences.getUserAgent(context),
        Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
  }

  public static ForstaServiceAccountManager createTextSecureAccountManager(Context context) {
    return new ForstaServiceAccountManager(TextSecurePreferences.getServer(context),
            null,
            TextSecurePreferences.getLocalAddress(context),
            TextSecurePreferences.getLocalDeviceId(context),
            TextSecurePreferences.getPushServerPassword(context),
            TextSecurePreferences.getUserAgent(context));
  }

  public static SignalServiceMessageReceiver createTextSecureMessageReceiver(Context context) {
    return new SignalServiceMessageReceiver(TextSecurePreferences.getServer(context),
            null,
            new DynamicCredentialsProvider(context),
            TextSecurePreferences.getUserAgent(context));
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalAddress(context) + "." + TextSecurePreferences.getLocalDeviceId(context);

    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

}
