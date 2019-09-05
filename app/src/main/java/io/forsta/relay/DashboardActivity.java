package io.forsta.relay;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.forsta.relay.AuthenticationRequiredActionBarActivity;
import io.forsta.relay.LoginActivity;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.atlas.model.AtlasJWT;
import io.forsta.librelay.atlas.model.AtlasTag;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.atlas.model.RelayContent;
import io.forsta.librelay.atlas.model.RelayDistribution;
import io.forsta.librelay.attachments.DatabaseAttachment;
import io.forsta.librelay.database.AttachmentDatabase;
import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.GroupDatabase;
import io.forsta.librelay.database.Identities;
import io.forsta.librelay.database.MessageAddressDatabase;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.messaging.MessageManager;
import io.forsta.librelay.recipients.DirectoryHelper;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.InvalidMessagePayloadException;
import io.forsta.librelay.util.TextSecurePreferences;

// TODO Remove all of this code for production release. This is for discovery and debug use.
public class DashboardActivity extends AuthenticationRequiredActionBarActivity {
  private static final String TAG = DashboardActivity.class.getSimpleName();
  private TextView mDebugText;
  private TextView mLoginInfo;
  private CheckBox mToggleSyncMessages;
  private Spinner mSpinner;
  private Spinner mConfigSpinner;
  private ScrollView mScrollView;
  private ProgressBar mProgressBar;
  private Button socketTester;
  

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_dashboard);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
    initView();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = new MenuInflater(DashboardActivity.this);
    menu.clear();
    inflater.inflate(R.menu.dashboard, menu);
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int i = item.getItemId();
    if (i == R.id.menu_dashboard_logout) {
      AtlasPreferences.clearLogin(DashboardActivity.this);
      startLoginIntent();
    } else if (i == R.id.menu_dashboard_clear_directory) {
      handleClearDirectory();
    } else if (i == R.id.menu_dashboard_clear_threads) {
      handleClearThreads();
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  private void initView() {
    mProgressBar = (ProgressBar) findViewById(R.id.dashboard_progress_bar);
    mScrollView = (ScrollView) findViewById(R.id.dashboard_scrollview);
    mLoginInfo = (TextView) findViewById(R.id.dashboard_login_info);
    mDebugText = (TextView) findViewById(R.id.debug_text);
    mSpinner = (Spinner) findViewById(R.id.dashboard_selector);
    List<String> options = new ArrayList<String>();
    options.add("Choose an option");
    options.add("Message Receipts");
    options.add("Messages Top 10");
    options.add("Threads");
    options.add("Forsta Contacts");
    options.add("Groups");
    options.add("Get API Users");
    options.add("Get API Groups");
    options.add("Get Directory");
    options.add("Message Tests");

    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
    mSpinner.setAdapter(adapter);
    mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position != 0) {
          showScrollView();
        }
        switch (position) {
          case 0:
            printLoginInformation();
            break;
          case 1:
            GetReceipts addresses = new GetReceipts();
            addresses.execute();
            break;
          case 2:
            GetMessages getMessages = new GetMessages();
            getMessages.execute();
            break;
          case 3:
            mDebugText.setText(printThreads());
            break;
          case 4:
            mDebugText.setText(printForstaContacts());
            break;
          case 5:
            mDebugText.setText(printGroups());
            break;
          case 6:
            mDebugText.setText("");
            mProgressBar.setVisibility(View.VISIBLE);
            GetTagUsers tagTask = new GetTagUsers();
            tagTask.execute();
            break;
          case 7:
            mDebugText.setText("");
            mProgressBar.setVisibility(View.VISIBLE);
            GetTagGroups groupTask = new GetTagGroups();
            groupTask.execute();
            break;
          case 8:
            mDebugText.setText("");
            GetDirectory directory = new GetDirectory();
            directory.execute();
            break;
          case 9:
            mDebugText.setText("");
            showScrollView();
            new MessageTests().execute();
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });

    mToggleSyncMessages = (CheckBox) findViewById(R.id.dashboard_toggle_sync_messages);
    mToggleSyncMessages.setChecked(AtlasPreferences.isCCSMDebug(DashboardActivity.this));
    mToggleSyncMessages.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AtlasPreferences.setCCSMDebug(DashboardActivity.this, mToggleSyncMessages.isChecked());
      }
    });
    printLoginInformation();
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... voids) {
        List<AtlasUser> users = new ArrayList<>();
        List<String> addresses = new ArrayList<>();
        AtlasUser me = AtlasUser.getLocalUser(DashboardActivity.this);
        addresses.add(me.getUid());
        users. add(me);
        DirectoryHelper.refreshDirectoryFor(DashboardActivity.this, addresses);
//        DbFactory.getContacts(DashboardActivity.this).updateUsers(users, false);
        return null;
      }
    }.execute();
  }

  private void showScrollView() {
    mScrollView.setVisibility(View.VISIBLE);
  }

  private void handleClearThreads() {
    new AlertDialog.Builder(DashboardActivity.this)
        .setTitle("Confirm")
        .setMessage("Are you sure?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            ThreadDatabase db = DbFactory.getThreadDatabase(DashboardActivity.this);
            db.deleteAllConversations();
            Toast.makeText(DashboardActivity.this, "All threads deleted", Toast.LENGTH_LONG).show();
          }
        }).show();
  }

  private void handleClearDirectory() {
    new AlertDialog.Builder(DashboardActivity.this)
        .setTitle("Confirm")
        .setMessage("Are you sure?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Contacts db = DbFactory.getContacts(DashboardActivity.this);
            db.removeAll();
            GroupDatabase groupDb = DbFactory.getGroupDatabase(DashboardActivity.this);
            groupDb.removeAllGroups();
            Toast.makeText(DashboardActivity.this, "All contacts and groups deleted", Toast.LENGTH_LONG).show();
          }
        }).show();
  }

  private void startLoginIntent() {
    Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
    startActivity(intent);
    finish();
  }

  private void printLoginInformation() {
    StringBuilder sb = new StringBuilder();
    String token = AtlasPreferences.getRegisteredKey(getApplicationContext());
    AtlasJWT jwt = new AtlasJWT(token);

    sb.append("API Host:");
    sb.append(BuildConfig.FORSTA_API_URL);
    sb.append("\n");
    sb.append("SIgnal Host:");
    sb.append(TextSecurePreferences.getServer(DashboardActivity.this));
    sb.append("\n");
    Date tokenExpire = jwt.getExpireDate();
    sb.append("Token Expires: ");
    sb.append(tokenExpire);
    sb.append("\n");

    AtlasUser user = AtlasUser.getLocalUser(DashboardActivity.this);
    sb.append("Org Id: ");
    sb.append(user.org_id);
    sb.append("\n");
    sb.append("User Id: ");
    sb.append(user.uid);
    sb.append("\n");
    sb.append("Tag Id: ");
    sb.append(user.tag_id);
    sb.append("\n");
    sb.append("Slug: ");
    sb.append(user.slug);
    sb.append("\n");
    sb.append("Phone: ");
    sb.append(user.phone);
    sb.append("\n");
    sb.append("Device ID: ");
    sb.append(TextSecurePreferences.getLocalDeviceId(DashboardActivity.this));
    sb.append("\n");

    mLoginInfo.setText(sb.toString());
  }

  private String printGroups() {
    GroupDatabase db = DbFactory.getGroupDatabase(getApplicationContext());
    Cursor cursor = db.getForstaGroups("");
    StringBuilder sb = new StringBuilder();
    while (cursor.moveToNext()) {
      int cols = cursor.getColumnCount();
      for (int i=0; i<cols; i++) {
        sb.append(cursor.getColumnName(i)).append(": ");
        try {
          sb.append(cursor.getString(i));
        } catch (Exception e) {

        }
        sb.append("\n");
      }
    }
    cursor.close();
    return sb.toString();
  }

  private String printForstaContacts() {
    Contacts db = DbFactory.getContacts(getApplicationContext());
    Cursor cursor = db.getAll();
    StringBuilder sb = new StringBuilder();
    while (cursor.moveToNext()) {
      int cols = cursor.getColumnCount();
      for (int i=0; i<cols; i++) {
        sb.append(cursor.getColumnName(i)).append(": ");
        sb.append(cursor.getString(i));
        sb.append("\n");
      }
    }
    cursor.close();
    return sb.toString();
  }

  private String printIdentities() {
    Identities idb = DbFactory.getIdentities(DashboardActivity.this);
    Cursor cdb = idb.getIdentities();
    StringBuilder sb = new StringBuilder();
    sb.append("\nIdentities\n");
    while (cdb.moveToNext()) {
      for (int i = 1; i < cdb.getColumnCount(); i++) {
        sb.append(cdb.getColumnName(i)).append(": ");
        try {
          sb.append(cdb.getString(i)).append("\n");
        } catch (Exception e) {
          sb.append(" bad value");
        }
      }
      sb.append("\n");
    }
    cdb.close();
    return sb.toString();
  }

  private String printThreads() {
    StringBuilder sb = new StringBuilder();
    ThreadDatabase tdb = DbFactory.getThreadDatabase(DashboardActivity.this);
    Cursor cursor = tdb.getConversationList();
    while (cursor != null && cursor.moveToNext()) {
      for (int i=0; i<cursor.getColumnCount(); i++) {
        if (!cursor.getColumnName(i).equals("snippet")) {
          sb.append(cursor.getColumnName(i)).append(": ");
          sb.append(cursor.getString(i)).append("\n");
        }
      }
      sb.append("\n");
    }
    cursor.close();
    return sb.toString();
  }

  private String printAllMessages() {
    StringBuilder sb = new StringBuilder();
    ThreadDatabase tdb = DbFactory.getThreadDatabase(DashboardActivity.this);
    AttachmentDatabase adb = DbFactory.getAttachmentDatabase(DashboardActivity.this);
    GroupDatabase gdb = DbFactory.getGroupDatabase(DashboardActivity.this);
    Cursor ccursor = tdb.getConversationList();
    ThreadDatabase.Reader treader = tdb.readerFor(ccursor);
    ThreadRecord trecord;
    while ((trecord = treader.getNext()) != null) {
      long tId = trecord.getThreadId();
      sb.append("Thread: ");
      sb.append(tId);
      sb.append("\n");
      Recipients trecipients = trecord.getRecipients();

      sb.append("Recipients").append("\n");
      sb.append(trecipients.getSortedAddresses()).append("\n");

      Cursor cursor = DbFactory.getMessageDatabase(DashboardActivity.this).getConversation(tId);
      MessageRecord record;
      MessageDatabase.Reader reader = DbFactory.getMessageDatabase(DashboardActivity.this).readerFor(cursor);
      MessageAddressDatabase addressDatabase = DbFactory.getMessageReceiptDatabase(DashboardActivity.this);

      while ((record = reader.getNext()) != null) {
        Recipient recipient = record.getIndividualRecipient();
        Recipients recipients = record.getRecipients();
        long threadId = record.getThreadId();
        CharSequence body = record.getDisplayBody();
        String rawBody = record.getBody();
        String messageRef = record.getMessageRef();
        int vote = record.getVoteCount();
        long timestamp = record.getTimestamp();
        Date dt = new Date(timestamp);
        long id = record.getId();
        String uid = record.getMessageId();
        List<Recipient> recipList = recipients.getRecipientsList();
        List<DatabaseAttachment> attachments = adb.getAttachmentsForMessage(record.getId());
        sb.append("Timestamp send: ").append(record.getTimestamp() + "");
        sb.append("\n");
        sb.append("ID: ").append(id + "");
        sb.append("\n");
        sb.append("Expiration Timer: ").append(record.isExpirationTimerUpdate());
        sb.append("\n");
        sb.append("Key Exchange: ").append(record.isBundleKeyExchange());
        sb.append("\n");
        sb.append("Sender: ").append(record.getIndividualRecipient().getAddress());
        sb.append("\n");
        sb.append("TYPE: ").append(Long.toHexString(record.getType()));
        sb.append("\n");
        sb.append("Message addresses: ").append("\n");
        List<String> list = addressDatabase.getAddressesListForId(id);
        for (String address : list) {
          sb.append(address).append("\n");
        }
        sb.append("\n");
        sb.append("Date: ");
        sb.append(dt.toString());
        sb.append("\n");
        sb.append("Message: ");
        sb.append(rawBody);
        sb.append("\n");
        sb.append("Message ADDRESS: ");
        sb.append(uid);
        sb.append("\n");
        sb.append("Message Ref: ");
        sb.append(messageRef);
        sb.append("\n");
        sb.append("Vote: ");
        sb.append(vote + "");
        sb.append("\n");
        sb.append("Attachments:");
        for (DatabaseAttachment item : attachments) {
          sb.append(item.getDataUri()).append(" ");
        }
        sb.append("\n");
        sb.append("\n");
      }
      sb.append("\n");
      reader.close();
    }
    sb.append("\n");

    treader.close();

    return sb.toString();
  }

  private class GetMessages extends AsyncTask<Void, Void, String> {

    @Override
    protected String doInBackground(Void... params) {
      return printAllMessages();
    }

    @Override
    protected void onPostExecute(String s) {
      mDebugText.setText(s);
    }
  }

  private class GetTagGroups extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return AtlasApi.getTags(DashboardActivity.this);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      List<AtlasTag> groups = AtlasApi.parseTagGroups(jsonObject);

      StringBuilder sb = new StringBuilder();
      for (AtlasTag group : groups) {
        String groupId = group.getUid();
        sb.append(groupId).append("\n");
        sb.append(group.description).append("\n");
        for (String number : group.members) {
          sb.append(number).append("\n");
        }
        sb.append("\n");
      }
      mDebugText.setText(sb.toString());
      mProgressBar.setVisibility(View.GONE);
    }
  }

  private class GetTagUsers extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return AtlasApi.getOrgUsers(DashboardActivity.this);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      List<AtlasUser> contacts = AtlasApi.parseUsers(getApplicationContext(), jsonObject);
      StringBuilder sb = new StringBuilder();
      for (AtlasUser user : contacts) {
        sb.append(user.phone).append(" ");
        sb.append(user.email).append(" ");
        sb.append(user.username).append("\n");
      }
      mDebugText.setText(sb.toString());
      mProgressBar.setVisibility(View.GONE);
    }
  }

  private class GetDirectory extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return AtlasApi.getUserDirectory(DashboardActivity.this, new ArrayList<String>());
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      mDebugText.setText(jsonObject.toString());
    }
  }

  private class GetReceipts extends AsyncTask<Void, Void, String> {

    @Override
    protected String doInBackground(Void... voids) {
      StringBuilder sb = new StringBuilder();

      Cursor cursor = DbFactory.getMessageReceiptDatabase(DashboardActivity.this).getAllAddresses();
      if (cursor != null) {
        while (cursor.moveToNext()) {
          sb.append(cursor.getString(cursor.getColumnIndex(MessageAddressDatabase.MESSAGE_ID))).append(" ");
          sb.append(cursor.getString(cursor.getColumnIndex(MessageAddressDatabase.ADDRESS))).append(" ");
          sb.append(cursor.getString(cursor.getColumnIndex(MessageAddressDatabase.DELIVERED))).append(" ");
          sb.append(cursor.getString(cursor.getColumnIndex(MessageAddressDatabase.READ))).append(" ");
          sb.append(cursor.getString(cursor.getColumnIndex(MessageAddressDatabase.TIMESTAMP))).append(" ");
          sb.append("\n");
        }
      }

      return sb.toString();
    }

    @Override
    protected void onPostExecute(String s) {
      mDebugText.setText(s);
    }
  }

  private class MessageTests extends AsyncTask<Void, String, Void> {

    @Override
    protected Void doInBackground(Void... params) {
      publishProgress("Bad JSON blob test.");
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("");
        publishProgress("Failed: empty string.");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("{}");
        publishProgress("Failed: empty object.");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }

      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[]");
        publishProgress("Failed: empty array");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      // No version object
      publishProgress("Bad version object test");
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[{virgin: 1}]");
        publishProgress("Failed: empty array");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      publishProgress("Bad messageType object test");
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[{version: 1, threadId: 1]}");
        publishProgress("Failed: invalid content type");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[{version: 1, threadId: 1, messageType: blank}]");
        publishProgress("Failed: invalid content type");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception: " + e.getMessage());
      }
      // No distribution
      publishProgress("Bad distribution object");
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[{version: 1, threadId: 1, messageType: content}]");
        publishProgress("Failed: no distribution object");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[{version: 1, threadId: 1, messageType: content, distribution: {}}]");
        publishProgress("Failed: empty distribution object");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }

      publishProgress("Bad distribution expression object");
      try {
        RelayContent relayContent = MessageManager.fromMessagBodyString("[{version: 1, threadId: 1, messageType: content, distribution: {expression: ''}}]");
        publishProgress("Failed: empty distribution expression");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }

      // Go through all existing messages and verify JSON.
      ThreadDatabase tdb = DbFactory.getThreadDatabase(DashboardActivity.this);
      MessageDatabase mdb = DbFactory.getMessageDatabase(DashboardActivity.this);
      Cursor ccursor = tdb.getConversationList();
      ThreadDatabase.Reader treader = tdb.readerFor(ccursor);
      ThreadRecord trecord;
      publishProgress("Verifying existing message records.");
      StringBuilder sb = new StringBuilder();
      int count = 0;
      int passCount = 0;
      int failCount = 0;
      while ((trecord = treader.getNext()) != null) {
        Cursor mcursor = mdb.getConversation(trecord.getThreadId());
        MessageDatabase.Reader mreader = mdb.readerFor(mcursor);
        MessageRecord mrecord;

        while ((mrecord = mreader.getNext()) != null) {
          count++;
          try {
            RelayContent relayContent = MessageManager.fromMessagBodyString(mrecord.getBody());
            passCount++;
          } catch (InvalidMessagePayloadException e) {
            failCount++;
            sb.append(e.getMessage()).append(": ").append(mrecord.getBody()).append("\n");
          }
        }
        mreader.close();
      }
      treader.close();
      publishProgress("Total Tested: " + count + " Passed: " + passCount + " Failed: " + failCount + "\n" + sb.toString());

      return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
      mDebugText.append(values[0] + "\n");
    }

    @Override
    protected void onPostExecute(Void v) {
      mDebugText.append("Complete");
    }
  }

  private class GetTagMath extends AsyncTask<Void, Void, RelayDistribution> {
    @Override
    protected RelayDistribution doInBackground(Void... voids) {
      return AtlasApi.getMessageDistribution(DashboardActivity.this, "@developer");
    }

    @Override
    protected void onPostExecute(RelayDistribution distribution) {
      Log.w(TAG, "" + distribution.getRecipients(DashboardActivity.this));
    }
  }
}

