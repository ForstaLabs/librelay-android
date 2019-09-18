package io.forsta.librelay.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import io.forsta.librelay.crypto.storage.TextSecureSessionStore;
import io.forsta.librelay.recipients.Recipient;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionStore;

import java.util.List;

public class SessionUtil {

  public static boolean hasSession(Context context, Recipient recipient) {
    return hasSession(context, recipient.getAddress());
  }

  public static boolean hasSession(Context context, @NonNull String addr) {
    SessionStore sessionStore = new TextSecureSessionStore(context);
    List<Integer> devices = sessionStore.getDeviceSessions(addr);
    for (int device : devices) {
      if (sessionStore.containsSession(new SignalProtocolAddress(addr, device))) {
        return true;
      }
    }
    return false;
  }
}
