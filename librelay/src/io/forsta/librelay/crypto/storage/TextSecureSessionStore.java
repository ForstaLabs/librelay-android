package io.forsta.librelay.crypto.storage;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import io.forsta.librelay.database.DbFactory;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import java.util.List;

public class TextSecureSessionStore implements SessionStore {

  private static final String TAG = TextSecureSessionStore.class.getSimpleName();

  private static final Object FILE_LOCK = new Object();

  @NonNull  private final Context context;

  public TextSecureSessionStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public SessionRecord loadSession(@NonNull SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      SessionRecord sessionRecord = DbFactory.getSessions(context).load(address.getName(), address.getDeviceId());

      if (sessionRecord == null) {
        Log.w(TAG, "No existing session information found.");
        return new SessionRecord();
      }

      return sessionRecord;
    }
  }

  @Override
  public List<Integer> getDeviceSessions(String address) {
    synchronized (FILE_LOCK) {
      return DbFactory.getSessions(context).getDeviceSessions(address);
    }
  }

  @Override
  public void storeSession(@NonNull SignalProtocolAddress address, @NonNull SessionRecord record) {
    synchronized (FILE_LOCK) {
      DbFactory.getSessions(context).store(address.getName(), address.getDeviceId(), record);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      SessionRecord sessionRecord = DbFactory.getSessions(context).load(address.getName(), address.getDeviceId());

      if (sessionRecord != null) {
        Log.d(TAG, "Current session version: " + sessionRecord.getSessionState().getSessionVersion());
      }

      return sessionRecord != null && sessionRecord.getSessionState().hasSenderChain();
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      DbFactory.getSessions(context).delete(address.getName(), address.getDeviceId());
    }
  }

  @Override
  public void deleteAllSessions(String address) {
    synchronized (FILE_LOCK) {
      DbFactory.getSessions(context).deleteAllFor(address);
    }
  }
}