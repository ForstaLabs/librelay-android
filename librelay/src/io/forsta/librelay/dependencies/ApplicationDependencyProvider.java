package io.forsta.librelay.dependencies;

import android.content.Context;
import android.support.annotation.NonNull;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;

import io.forsta.librelay.service.ForstaServiceAccountManager;

/**
 * Implementation of {@link ApplicationDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements ApplicationDependencies.Provider {

  private static final String TAG = ApplicationDependencyProvider.class.getSimpleName();

  private final Context context;

  private ForstaServiceAccountManager accountManager;
  private SignalServiceMessageSender messageSender;
  private SignalServiceMessageReceiver messageReceiver;

  public ApplicationDependencyProvider(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  @NonNull
  @Override
  public ForstaServiceAccountManager getSignalServiceAccountManager() {
    if (accountManager == null) {
      accountManager = TextSecureCommunicationFactory.createTextSecureAccountManager(context);
    }
    return accountManager;
  }

  @NonNull
  @Override
  public SignalServiceMessageSender getSignalServiceMessageSender() {
    if (messageSender == null) {
      messageSender = TextSecureCommunicationFactory.createMessageSender(context);
    }
    return messageSender;
  }

  @NonNull
  @Override
  public SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
    if (messageReceiver == null) {
      messageReceiver = TextSecureCommunicationFactory.createTextSecureMessageReceiver(context);
    }
    return messageReceiver;
  }
}