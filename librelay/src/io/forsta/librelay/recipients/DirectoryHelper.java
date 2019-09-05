package io.forsta.librelay.recipients;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;

import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.AtlasSyncAdapter;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.R;

import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;

public class DirectoryHelper {
  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void resetDirectory(Context context) throws IOException {
    refreshDirectory(context, TextSecurePreferences.getLocalAddress(context), true);
  }

  public static void refreshDirectory(@NonNull Context context) throws IOException {
    refreshDirectory(context, TextSecurePreferences.getLocalAddress(context), false);
  }

  public static void refreshDirectory(@NonNull Context context, @NonNull String localNumber) throws IOException {
    refreshDirectory(context, localNumber, false);
  }

  private static void refreshDirectory(@NonNull Context context, @NonNull String localNumber, boolean resetDirectory) throws IOException {
    JSONObject localUser = AtlasApi.getLocalForstaUser(context);
    if (localUser == null || !localUser.has("id")) {
      return;
    }
    AtlasPreferences.setForstaUser(context, localUser.toString());

    JSONObject orgResponse = AtlasApi.getOrg(context);
    if (orgResponse != null && orgResponse.has("id")) {
      AtlasPreferences.setForstaOrg(context, orgResponse.toString());
    }

    AtlasApi.syncAtlasContacts(context, resetDirectory);
    notifyRefresh(context);
  }

  public static void refreshDirectoryFor(@NonNull Context context, @NonNull Recipients recipients) {
    try {
      List<String> addresses = recipients.toNumberStringList(false);
      if (addresses.size() > 0) {
        AtlasApi.syncAtlasContacts(context, addresses);
        notifyRefresh(context);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void refreshDirectoryFor(Context context, List<String> addresses) {
    try {
      if (addresses.size() > 0) {
        AtlasApi.syncAtlasContacts(context, addresses);
        notifyRefresh(context);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void notifyRefresh(Context context) {
    Log.w(TAG, "notifyRefresh. Sending broadcast: ATLAS_SYNC_COMPLETE");
    context.sendBroadcast(new Intent(AtlasSyncAdapter.ATLAS_SYNC_COMPLETE));
  }

  public static Optional<Account> getOrCreateAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType("io.forsta.librelay");

    Optional<Account> account;

    if (accounts.length == 0) account = createAccount(context);
    else                      account = Optional.of(accounts[0]);

    if (account.isPresent() && !ContentResolver.getSyncAutomatically(account.get(), ContactsContract.AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account.get(), ContactsContract.AUTHORITY, true);
    }

    return account;
  }

  private static Optional<Account> createAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account        account        = new Account(context.getString(R.string.app_name), "io.forsta.librelay");

    if (accountManager.addAccountExplicitly(account, null, null)) {
      Log.w(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return Optional.of(account);
    } else {
      Log.w(TAG, "Failed to create account!");
      return Optional.absent();
    }
  }
}
