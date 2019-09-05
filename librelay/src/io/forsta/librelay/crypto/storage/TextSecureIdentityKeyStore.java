package io.forsta.librelay.crypto.storage;

import android.content.Context;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.crypto.IdentityKeyUtil;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.IdentityKeyStore;

public class TextSecureIdentityKeyStore implements IdentityKeyStore {

  private final Context context;

  public TextSecureIdentityKeyStore(Context context) {
    this.context = context;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return IdentityKeyUtil.getIdentityKeyPair(context);
  }

  @Override
  public int getLocalRegistrationId() {
    return TextSecurePreferences.getLocalRegistrationId(context);
  }

  @Override
  public void saveIdentity(String name, IdentityKey identityKey) {
    DbFactory.getIdentities(context).saveIdentity(name, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
    boolean trusted     = DbFactory.getIdentities(context)
                                         .isValidIdentity(name, identityKey);

    if (trusted) {
      return true;
    } else {
      return false;
    }
  }
}
