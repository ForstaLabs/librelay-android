/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.relay;

import android.accounts.Account;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import io.forsta.relay.ApplicationContext;
import io.forsta.relay.AuthenticationRequiredActionBarActivity;
import io.forsta.relay.ConversationActivity;
import io.forsta.relay.ConversationListArchiveActivity;
import io.forsta.relay.LoginActivity;
import io.forsta.relay.ApplicationPreferencesActivity;

import io.forsta.librelay.ConversationListFragment;
import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.atlas.AtlasSyncAdapter;
import io.forsta.librelay.atlas.model.AtlasOrg;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.jobs.DirectoryRefreshJob;
import io.forsta.librelay.notifications.MessageNotifier;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.DynamicLanguage;
import io.forsta.librelay.util.DynamicTheme;

public class ConversationListActivity extends AuthenticationRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener, ConversationListFragment.NewConversationClickListener
{
  private static final String TAG = ConversationListActivity.class.getSimpleName();
  private static IntentFilter syncIntentFilter = new IntentFilter(AtlasSyncAdapter.ATLAS_SYNC_COMPLETE);
  private BroadcastReceiver syncReceiver;

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment fragment;
  private LinearLayout syncIndicator;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onCreate(Bundle savedState) {
    super.onCreate(savedState);
    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
    getSupportActionBar().setCustomView(io.forsta.librelay.R.layout.conversation_list_title_view);

    syncIndicator = (LinearLayout) findViewById(R.id.forsta_sync_indicator);
    syncReceiver = new ContactsSyncReceiver();
    registerReceiver(syncReceiver, syncIntentFilter);
    fragment = initFragment(R.id.forsta_conversation_list, new ConversationListFragment(), dynamicLanguage.getCurrentLocale());
    fragment.setNewConversationClickListener(this);

    if (AtlasPreferences.isRegisteredAtlas(ConversationListActivity.this)) {
      RefreshAtlasToken task = new RefreshAtlasToken();
      task.execute();

      if (AtlasPreferences.getForstaContactSync(this) == -1) {
        syncIndicator.setVisibility(View.VISIBLE);
        Account account = AtlasSyncAdapter.getAccount(getApplicationContext());
        ContentResolver.requestSync(account, AtlasSyncAdapter.AUTHORITY, Bundle.EMPTY);
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  }

  @Override
  protected void onPause() {
    syncIndicator.setVisibility(View.GONE);
    super.onPause();
  }

  @Override
  public void onDestroy() {
    if (syncReceiver != null) unregisterReceiver(syncReceiver);
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(io.forsta.librelay.R.menu.text_secure_normal, menu);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int i = item.getItemId();
    if (i == io.forsta.librelay.R.id.menu_settings) {
      handleDisplaySettings();
      return true;
    } else if (i == io.forsta.librelay.R.id.menu_mark_all_read) {
      handleMarkAllRead();
      return true;
    } else if (i == io.forsta.librelay.R.id.menu_help) {
      handleHelp();
      return true;
    } else if (i == io.forsta.librelay.R.id.menu_archive) {
      onSwitchToArchive();
      return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    if (recipients == null || recipients.isEmpty()) {
      Toast.makeText(ConversationListActivity.this, "Error. This thread has no recipients. Please remove it.", Toast.LENGTH_LONG).show();
    } else {
      Intent intent = new Intent(this, ConversationActivity.class);
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

      startActivity(intent);
      overridePendingTransition(io.forsta.librelay.R.anim.slide_from_right, io.forsta.librelay.R.anim.fade_scale_out);
    }
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    startActivity(intent);
  }

  private void handleLogout() {
    AtlasPreferences.clearLogin(ConversationListActivity.this);
    Intent intent = new Intent(ConversationListActivity.this, LoginActivity.class);
    startActivity(intent);
    finish();
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
  }

  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DbFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
        MessageNotifier.updateNotification(ConversationListActivity.this);
        return null;
      }
    }.execute();
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://support.forsta.io")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, io.forsta.librelay.R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onClick() {
    startActivity(new Intent(this, NewConversationActivity.class));
  }

  private class ContactsSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "Received broadcast intent: Sync complete. Notifying list adapter of change.");
      syncIndicator.setVisibility(View.GONE);
      fragment.getListAdapter().notifyDataSetChanged();
    }
  }

  public class RefreshAtlasToken extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return AtlasApi.atlasRefreshToken(ConversationListActivity.this);
    }

    @Override
    protected void onPostExecute(JSONObject response) {
      if (AtlasApi.isErrorResponse(response)) {
        if (AtlasApi.isUnauthorizedResponse(response)) {
          handleLogout();
        } else {
          Toast.makeText(ConversationListActivity.this, "Error response from server.", Toast.LENGTH_LONG).show();
        }
      } else {
        ApplicationContext.getInstance(getApplicationContext()).getJobManager().add(new DirectoryRefreshJob(getApplicationContext()));
        AtlasOrg atlasOrg = AtlasOrg.getLocalForstaOrg(ConversationListActivity.this);
        if (atlasOrg != null) {
          TextView title = (TextView) getSupportActionBar().getCustomView().findViewById(io.forsta.librelay.R.id.conversation_list_title);
          title.setText(atlasOrg.getName().toLowerCase());
        }
      }
    }
  }
}
