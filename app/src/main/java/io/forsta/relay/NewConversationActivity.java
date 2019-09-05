/**
 * Copyright (C) 2015 Open Whisper Systems
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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AlertDialog;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.relay.ConversationActivity;

import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.atlas.model.RelayDistribution;
import io.forsta.librelay.components.SelectedRecipient;
import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.GroupDatabase;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.recipients.DirectoryHelper;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.Util;

/**
 * Activity container for starting a new conversation.
 *
 * @author Moxie Marlinspike
 *
 */
public class NewConversationActivity extends ContactSelectionActivity {

  private static final String TAG = NewConversationActivity.class.getSimpleName();
  private RemoveRecipientClickListener selectedRecipientRemoveListener;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    getToolbar().setShowCustomNavigationButton(false);
    getSupportActionBar().setDisplayShowHomeEnabled(false);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true); //Consider removing this to allow more room for search

    initListeners();
  }

  private void initListeners() {
    toolbar.setSearchOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showProgressBar();
        String searchText = toolbar.getSearchText();
        // check to see if it is a phone number or email. If not, assume expression.
        if (PhoneNumberUtils.isGlobalPhoneNumber(searchText)) {
          try {
            String e164Number = Util.canonicalizeNumberE164(searchText);
            lookupUsersByPhone(e164Number);
          } catch (InvalidNumberException e) {
            Toast.makeText(NewConversationActivity.this, "Invalid phone", Toast.LENGTH_LONG).show();
          }
        } else if (Patterns.EMAIL_ADDRESS.matcher(searchText).matches()) {
          lookupUsersByEmail(searchText);
        } else {
          if (!searchText.startsWith("@")) {
            searchText = "@" + searchText;
          }
          lookupUsersByExpression(searchText);
        }
      }
    });

    toolbar.setCreateConversationListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleCreateConversation();
      }
    });
    selectedRecipientRemoveListener = new RemoveRecipientClickListener();
    threadType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        Log.w(TAG, "Checked: " + checkedId);
      }
    });
  }

  private class RemoveRecipientClickListener implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      removeChip(view);
      updateToggleBar();
    }
  }

  @Override
  public void onContactSelected(final String address, final int type) {
    if (type == Contacts.ATLAS_GROUP_TYPE) {
      GroupDatabase.GroupRecord group = DbFactory.getGroupDatabase(NewConversationActivity.this).getGroup(address);
      if (group == null || TextUtils.isEmpty(group.getTag())) {
        Toast.makeText(NewConversationActivity.this, "Unable to retrieve group information.", Toast.LENGTH_LONG).show();
        return ;
      }
      addChip(group.getUid(), group.getTag());
    } else {
      Recipient recipient = RecipientFactory.getRecipient(NewConversationActivity.this, address, false);
      if (recipient == null || TextUtils.isEmpty(recipient.getSlug())) {
        Toast.makeText(NewConversationActivity.this, "Unable to retrieve user information.", Toast.LENGTH_LONG).show();
        return ;
      }
      addChip(recipient.getAddress(), recipient.getLocalTag());
    }
    toolbar.clear();
    updateToggleBar();
    contactsFragment.resetQueryFilter();
  }

  private void removeChip(View view) {
    SelectedRecipient recipient = (SelectedRecipient) view;
    contactsFragment.removeAddress(recipient.getAddress());
    expressionElements.removeView(view);
  }

  private void addChip(String address, String tag) {
    if (!contactsFragment.containsAddress(address)) {
      SelectedRecipient recipientChip = new SelectedRecipient(this);
      recipientChip.setAddress(address);
      recipientChip.setText(tag); // This could also be name. Address and tag info are stored in selectedTags.
      recipientChip.setOnClickListener(selectedRecipientRemoveListener);
      expressionElements.addView(recipientChip);
    }
  }

  private void handleCreateConversation() {
    final AtlasUser localUser = AtlasUser.getLocalUser(NewConversationActivity.this);
    final int type = threadType.getCheckedRadioButtonId() == R.id.new_conversation_button_announcement ? 1 : 0;
    if (localUser == null) {
      Toast.makeText(NewConversationActivity.this, "Unable to retrieve local user information.", Toast.LENGTH_LONG).show();
      return;
    }
    if (contactsFragment.isEmptySelected()) {
      Toast.makeText(NewConversationActivity.this, "Please select a recipient", Toast.LENGTH_LONG).show();
      return;
    }

    // Look up matching group addresses and matching contact addresses in db
    GroupDatabase groupTable = DbFactory.getGroupDatabase(NewConversationActivity.this);
    Contacts contactTable = DbFactory.getContacts(NewConversationActivity.this);
    StringBuilder expression = new StringBuilder();
    for (String address : contactsFragment.getSelectedAddresses()) {
      Recipient recipient = contactTable.getRecipient(address);
      if (recipient != null) {
        expression.append(recipient.getFullTag()).append(" + ");;
      } else {
        GroupDatabase.GroupRecord group = groupTable.getGroup(address);
        if (group != null) {
          expression.append(group.getExpression(localUser)).append(" + ");;
        }
      }
    }

    Log.w(TAG, "Expression: " + expression);

    new AsyncTask<String, Void, RelayDistribution>() {
      @Override
      protected RelayDistribution doInBackground(String... params) {
        String expression = params[0];
        expression = expression.substring(0, expression.lastIndexOf(" + "));
        RelayDistribution initialDistribution = AtlasApi.getMessageDistribution(NewConversationActivity.this, expression);
        if (initialDistribution.hasWarnings() || initialDistribution.userIds.contains(localUser.getUid()) || !initialDistribution.hasRecipients()) {
          return initialDistribution;
        } else {
          String newExpression = initialDistribution.pretty + " + @" + localUser.getTag();
          return AtlasApi.getMessageDistribution(NewConversationActivity.this, newExpression);
        }
      }

      @Override
      protected void onPostExecute(final RelayDistribution distribution) {
        if (distribution.hasWarnings()) {
          hideProgressBar();
          Toast.makeText(NewConversationActivity.this, distribution.getWarnings(), Toast.LENGTH_LONG).show();
          return;
        }

        if (distribution.hasRecipients()) {
          final Recipients recipients = RecipientFactory.getRecipientsFromStrings(NewConversationActivity.this, distribution.getRecipients(NewConversationActivity.this), false);
          final ThreadRecord forstaThread = DbFactory.getThreadDatabase(NewConversationActivity.this).getThreadForDistribution(distribution.universal, type);
          if (forstaThread == null) {
            createConversation(DbFactory.getThreadDatabase(NewConversationActivity.this).allocateThread(recipients, distribution, type), recipients);
          } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(NewConversationActivity.this);
            builder.setTitle("New Conversation")
                .setMessage("Use existing conversation or create new?")
                .setPositiveButton("New", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {
                    createConversation(DbFactory.getThreadDatabase(NewConversationActivity.this).allocateThread(recipients, distribution, type), recipients);
                  }
                })
                .setNegativeButton("Existing", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {
                    createConversation(forstaThread, recipients);
                  }
                })
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                  @Override
                  public void onCancel(DialogInterface dialogInterface) {
                    hideProgressBar();
                  }
                })
                .create();
            builder.show();
            showProgressBar();
          }
        } else {
          hideProgressBar();
          Toast.makeText(NewConversationActivity.this, "No recipients found in expression.", Toast.LENGTH_LONG).show();
        }
      }
    }.execute(expression.toString());
  }

  private void createConversation(ThreadRecord forstaThread, Recipients recipients) {
    long threadId = forstaThread.getThreadId();
    Intent intent = new Intent(NewConversationActivity.this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
    intent.setDataAndType(getIntent().getData(), getIntent().getType());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    startActivity(intent);
    finish();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int i = item.getItemId();
    if (i == android.R.id.home) {
      super.onBackPressed();
      return true;
    } else if (i == io.forsta.librelay.R.id.menu_refresh) {
      handleManualRefresh();
      return true;
    } else if (i == io.forsta.librelay.R.id.menu_reset) {
      handleReset();
      return true;
    }

    return false;
  }

  private void handleManualRefresh() {
    contactsFragment.setRefreshing(true);
    onRefresh();
  }

  private void handleReset() {
    contactsFragment.setRefreshing(true);
    resetDirectory();
  }

  @Override
  protected boolean onPrepareOptionsPanel(View view, Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();
    inflater.inflate(io.forsta.librelay.R.menu.new_conversation_activity, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void lookupUsersByPhone(final String number) {
    new AsyncTask<String, Void, Void>() {

      @Override
      protected Void doInBackground(String... strings) {
        String phone = strings[0];
        Set<String> numbers = new HashSet<>();
        numbers.add(phone);
        List<AtlasUser> users = AtlasApi.getAtlasUsersByPhone(NewConversationActivity.this, numbers);
        List<String> results = new ArrayList<>();
        for (AtlasUser user: users) {
          results.add(user.getUid());
        }
        if (results.size() > 0) {
          DirectoryHelper.refreshDirectoryFor(NewConversationActivity.this, results);
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        hideProgressBar();
        toolbar.setSearchText("");
        contactsFragment.setQueryFilter("");
      }
    }.execute(number);
  }

  private void lookupUsersByEmail(String email) {
    new AsyncTask<String, Void, Void>() {

      @Override
      protected Void doInBackground(String... strings) {
        String email = strings[0];
        Set<String> emails = new HashSet<>();
        emails.add(email);
        List<AtlasUser> users = AtlasApi.getAtlasUsersByEmail(NewConversationActivity.this, emails);
        List<String> results = new ArrayList<>();
        for (AtlasUser user: users) {
          results.add(user.getUid());
        }
        if (results.size() > 0) {
          DirectoryHelper.refreshDirectoryFor(NewConversationActivity.this, results);
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        hideProgressBar();
        toolbar.setSearchText("");
        contactsFragment.setQueryFilter("");
      }
    }.execute(email);
  }

  private void lookupUsersByExpression(String expression) {
    new AsyncTask<String, Void, RelayDistribution>() {
      @Override
      protected RelayDistribution doInBackground(String... strings) {
        RelayDistribution distribution = AtlasApi.getMessageDistribution(NewConversationActivity.this, strings[0]);
        if (distribution.hasRecipients()) {
          DirectoryHelper.refreshDirectoryFor(NewConversationActivity.this, distribution.getRecipients(NewConversationActivity.this));
        }
        return distribution;
      }

      @Override
      protected void onPostExecute(RelayDistribution distribution) {
        hideProgressBar();
        if (distribution.hasWarnings()) {
          Toast.makeText(NewConversationActivity.this, distribution.getWarnings(), Toast.LENGTH_LONG).show();
        }
        contactsFragment.setQueryFilter(toolbar.getSearchText());
      }
    }.execute(expression);
  }

}
