package io.forsta.librelay.atlas;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.R;
import io.forsta.librelay.recipients.DirectoryHelper;
import io.forsta.librelay.util.TextSecurePreferences;

/**
 * Created by jlewis on 5/8/17.
 */

public class AtlasSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = AtlasSyncAdapter.class.getSimpleName();
  public static final String ATLAS_SYNC_COMPLETE = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".ATLAS_SYNC_COMPLETE";
  public static final String AUTHORITY = BuildConfig.FORSTA_PROVIDER + ".atlas";
  private ContentResolver contentResolver;


  public AtlasSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);

    contentResolver = context.getContentResolver();
  }

  @Override
  public void onPerformSync(Account account, Bundle bundle, String s, ContentProviderClient contentProviderClient, SyncResult syncResult) {
    Log.w(TAG, "onPerformSync(" + s +")");

    if (TextSecurePreferences.isPushRegistered(getContext())) {
      try {
        DirectoryHelper.refreshDirectory(getContext());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static Account getAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType(context.getString(R.string.forsta_account_type));
    Account account;

    if (accounts.length == 0) {
      account = new Account("Atlas Contacts", context.getString(R.string.forsta_account_type));
      if (accountManager.addAccountExplicitly(account, null, null)) {
        ContentResolver.setIsSyncable(account, AUTHORITY, 1);
      }
    } else {
      account = accounts[0];
    }

    if (account != null && !ContentResolver.getSyncAutomatically(account, AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
      ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, 60l * 60l * 4);
    }

    return account;
  }
}
