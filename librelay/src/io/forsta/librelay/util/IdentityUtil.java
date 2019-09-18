package io.forsta.librelay.util;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.UiThread;

import io.forsta.librelay.crypto.storage.TextSecureSessionStore;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.util.concurrent.ListenableFuture;
import io.forsta.librelay.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class IdentityUtil {

  @UiThread
  public static ListenableFuture<Optional<IdentityKey>> getRemoteIdentityKey(final Context context,
                                                                             final Recipient recipient)
  {
    final SettableFuture<Optional<IdentityKey>> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Optional<IdentityKey>>() {
      @Override
      protected Optional<IdentityKey> doInBackground(Recipient... recipient) {
        SessionStore sessionStore = new TextSecureSessionStore(context);
        String addr = recipient[0].getAddress();
        List<Integer> devices = sessionStore.getDeviceSessions(addr);
        for (int device : devices) {
          SessionRecord record = sessionStore.loadSession(new SignalProtocolAddress(addr, device));
          if (record != null) {
            return Optional.fromNullable(record.getSessionState().getRemoteIdentityKey());
          }
        }
        return Optional.absent();
      }

      @Override
      protected void onPostExecute(Optional<IdentityKey> result) {
        future.set(result);
      }
    }.execute(recipient);

    return future;
  }

}
