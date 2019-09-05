package io.forsta.librelay.crypto.storage;

import android.content.Context;
import android.support.annotation.NonNull;

import io.forsta.librelay.database.DbFactory;

import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;

import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, SignedPreKeyStore {
  private static final String TAG                    = TextSecurePreKeyStore.class.getSimpleName();
  public  static final String PREKEY_DIRECTORY        = "prekeys";
  public  static final String SIGNED_PREKEY_DIRECTORY = "signed_prekeys";

  private static final int    CURRENT_VERSION_MARKER = 1;
  private static final Object FILE_LOCK              = new Object();
  @NonNull  private final Context      context;

  public TextSecurePreKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    synchronized (FILE_LOCK) {
      PreKeyRecord preKeyRecord = DbFactory.getPrekeys(context).getPreKey(preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    synchronized (FILE_LOCK) {
      SignedPreKeyRecord signedPreKeyRecord = DbFactory.getSignedPrekeys(context).getSignedPreKey(signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    synchronized (FILE_LOCK) {
      return DbFactory.getSignedPrekeys(context).getAllSignedPreKeys();
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    synchronized (FILE_LOCK) {
      DbFactory.getPrekeys(context).insertPreKey(preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    synchronized (FILE_LOCK) {
      DbFactory.getSignedPrekeys(context).insertSignedPreKey(signedPreKeyId, record);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return DbFactory.getPrekeys(context).getPreKey(preKeyId) != null;
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return DbFactory.getSignedPrekeys(context).getSignedPreKey(signedPreKeyId) != null;
  }

  @Override
  public void removePreKey(int preKeyId) {
    DbFactory.getPrekeys(context).removePreKey(preKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    DbFactory.getSignedPrekeys(context).removeSignedPreKey(signedPreKeyId);
  }
}