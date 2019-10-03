package io.forsta.relay;

import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.color.MaterialColors;
import io.forsta.librelay.database.ThreadPreferenceDatabase;
import io.forsta.librelay.preferences.ThreadPreferenceFragment;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.messaging.MessageSender;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.DynamicLanguage;

public class ThreadPreferenceActivity extends AuthenticationRequiredActionBarActivity {
  public static final String TAG = ThreadPreferenceActivity.class.getSimpleName();

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private long threadId;
  private ThreadRecord threadDetail;
  private TextView threadRecipients;
  private EditText forstaTitle;
  private ImageButton forstaSaveTitle;
  private Recipients recipients;
  private Handler handler = new Handler();
  private ContentObserver threadObserver;

  @Override
  public void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle instanceState) {
    super.onCreate(instanceState);
    setContentView(R.layout.activity_thread_preference);
    threadId = getIntent().getLongExtra(ThreadPreferenceFragment.THREAD_ID_EXTRA, -1);
    initializeToolbar();
    initializeThread();
    initializeListeners();

    Bundle bundle = new Bundle();
    bundle.putLong(ThreadPreferenceFragment.THREAD_ID_EXTRA, threadId);
    initFragment(R.id.thread_preference_fragment, new ThreadPreferenceFragment(), null, bundle);

    threadObserver = new ContentObserver(handler) {
      @Override
      public void onChange(boolean selfChange) {
        initializeThread();
      }
    };
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);

    getContentResolver().registerContentObserver(Uri.parse(ThreadDatabase.CONVERSATION_URI + threadId), true, threadObserver);
  }

  @Override
  protected void onPause() {
    super.onPause();
    getContentResolver().unregisterContentObserver(threadObserver);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        super.onBackPressed();
        return true;
    }

    return false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.thread_preference_fragment);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  private void initializeToolbar() {
    ThreadPreferenceDatabase prefDb = DbFactory.getThreadPreferenceDatabase(this);
    ThreadPreferenceDatabase.ThreadPreference threadPreference = prefDb.getThreadPreferences(threadId);

    MaterialColor color = threadPreference.getColor();
    if (color == null) {
      color = MaterialColors.getRandomConversationColor();
      prefDb.setColor(threadId, color);
    }

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
    setStatusBarColor(color.toStatusBarColor(this));

    threadRecipients = (TextView) findViewById(R.id.forsta_thread_recipients);
    forstaTitle = (EditText) findViewById(R.id.forsta_thread_title);
    forstaSaveTitle = (ImageButton) findViewById(R.id.forsta_title_save_button);
    forstaSaveTitle.setOnClickListener(new TitleSaveClickListener());
  }

  private void initializeThread() {
    recipients = DbFactory.getThreadDatabase(ThreadPreferenceActivity.this).getRecipientsForThreadId(threadId);
    threadDetail = DbFactory.getThreadDatabase(ThreadPreferenceActivity.this).getThread(threadId);
    threadRecipients.setText(threadDetail.getPrettyExpression());
    if (!TextUtils.isEmpty(threadDetail.getTitle())) {
      forstaTitle.setText(threadDetail.getTitle());
    } else {
      forstaTitle.setHint("Add a Title");
    }

    getSupportActionBar().setTitle(TextUtils.isEmpty(threadDetail.getTitle()) ? recipients.toCondensedString(ThreadPreferenceActivity.this) : threadDetail.getTitle());
  }

  private void initializeListeners() {
    forstaTitle.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        String saved = threadDetail.getTitle() != null ? threadDetail.getTitle() : "";
        if (!saved.equals(s.toString())) {
          forstaSaveTitle.setVisibility(View.VISIBLE);
        } else {
          forstaSaveTitle.setVisibility(View.GONE);
        }
      }

      @Override
      public void afterTextChanged(Editable s) {

      }
    });
  }

  private class TitleSaveClickListener implements View.OnClickListener {

    @Override
    public void onClick(View view) {

      new AsyncTask<String, Void, Void>() {
        @Override
        protected Void doInBackground(String... params) {
          DbFactory.getThreadDatabase(ThreadPreferenceActivity.this).updateThreadTitle(threadId, params[0]);
          MessageSender.sendThreadUpdate(ThreadPreferenceActivity.this, threadId);
          return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
          forstaSaveTitle.setVisibility(View.GONE);
          initializeThread();
          Toast.makeText(ThreadPreferenceActivity.this, "Conversation title saved", Toast.LENGTH_LONG).show();
        }
      }.execute(forstaTitle.getText().toString());
    }
  }

}
