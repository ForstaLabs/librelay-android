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

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;

import io.forsta.relay.AuthenticationRequiredActionBarActivity;

import io.forsta.librelay.ConversationItem;
import io.forsta.librelay.MessageDetailsRecipientAdapter;
import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.loaders.MessageDetailsLoader;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.notifications.MessageNotifier;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.DateUtils;
import io.forsta.librelay.util.DynamicLanguage;
import io.forsta.librelay.util.DynamicTheme;
import io.forsta.librelay.util.ExpirationUtil;
import io.forsta.librelay.util.Util;

/**
 * @author Jake McGinty
 */
public class MessageDetailsActivity extends AuthenticationRequiredActionBarActivity implements LoaderCallbacks<Cursor>, Recipients.RecipientsModifiedListener {
  private final static String TAG = MessageDetailsActivity.class.getSimpleName();

  public final static String MASTER_SECRET_EXTRA  = "master_secret";
  public final static String MESSAGE_ID_EXTRA     = "message_id";
  public final static String THREAD_ID_EXTRA      = "thread_id";
  public final static String IS_PUSH_GROUP_EXTRA  = "is_push_group";
  public final static String RECIPIENTS_IDS_EXTRA = "recipients_ids";

  private long             threadId;
  private boolean          isPushGroup;
  private ConversationItem conversationItem;
  private ViewGroup        itemParent;
  private View             metadataContainer;
  private View             expiresContainer;
  private TextView         errorText;
  private TextView         sentDate;
  private TextView         receivedDate;
  private TextView         expiresInText;
  private View             receivedContainer;
  private TextView         toFrom;
  private ListView         recipientsList;
  private LayoutInflater   inflater;

  private DynamicTheme     dynamicTheme    = new DynamicTheme();
  private DynamicLanguage  dynamicLanguage = new DynamicLanguage();

  private boolean running;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.message_details_activity);
    running = true;

    initializeResources();
    initializeActionBar();
    getSupportLoaderManager().initLoader(0, null, this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(R.string.AndroidManifest__message_details);

    MessageNotifier.setVisibleThread(threadId);
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    running = false;
  }

  private void initializeActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    Recipients recipients = RecipientFactory.getRecipientsForIds(this, getIntent().getLongArrayExtra(RECIPIENTS_IDS_EXTRA), true);
    recipients.addListener(this);

    setActionBarColor(recipients.getColor());
  }

  private void setActionBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(color.toStatusBarColor(this));
    }
  }

  @Override
  public void onModified(final Recipients recipients) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setActionBarColor(recipients.getColor());
      }
    });
  }

  private void initializeResources() {
    inflater       = LayoutInflater.from(this);
    View header = inflater.inflate(io.forsta.librelay.R.layout.message_details_header, recipientsList, false);

    threadId          = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    isPushGroup       = getIntent().getBooleanExtra(IS_PUSH_GROUP_EXTRA, false);
    itemParent        = (ViewGroup) header.findViewById(io.forsta.librelay.R.id.item_container);
    recipientsList    = (ListView ) findViewById(R.id.recipients_list);
    metadataContainer =             header.findViewById(io.forsta.librelay.R.id.metadata_container);
    errorText         = (TextView ) header.findViewById(io.forsta.librelay.R.id.error_text);
    sentDate          = (TextView ) header.findViewById(io.forsta.librelay.R.id.sent_time);
    receivedContainer =             header.findViewById(io.forsta.librelay.R.id.received_container);
    receivedDate      = (TextView ) header.findViewById(io.forsta.librelay.R.id.received_time);
    toFrom            = (TextView ) header.findViewById(io.forsta.librelay.R.id.tofrom);
    expiresContainer  =             header.findViewById(io.forsta.librelay.R.id.expires_container);
    expiresInText     = (TextView)  header.findViewById(io.forsta.librelay.R.id.expires_in);
    recipientsList.setHeaderDividersEnabled(false);
    recipientsList.addHeaderView(header, null, false);
  }

  private void updateTime(MessageRecord messageRecord) {
    if (messageRecord.isPending() || messageRecord.isFailed()) {
      sentDate.setText("-");
      receivedContainer.setVisibility(View.GONE);
    } else {
      Locale           dateLocale    = dynamicLanguage.getCurrentLocale();
      SimpleDateFormat dateFormatter = DateUtils.getDetailedDateFormatter(this, dateLocale);
      sentDate.setText(dateFormatter.format(new Date(messageRecord.getDateSent())));

      if (messageRecord.getDateReceived() != messageRecord.getDateSent() && !messageRecord.isOutgoing()) {
        receivedDate.setText(dateFormatter.format(new Date(messageRecord.getDateReceived())));
        receivedContainer.setVisibility(View.VISIBLE);
      } else {
        receivedContainer.setVisibility(View.GONE);
      }
    }
  }

  private void updateExpirationTime(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() <= 0 || messageRecord.getExpireStarted() <= 0) {
      expiresContainer.setVisibility(View.GONE);
      return;
    }

    expiresContainer.setVisibility(View.VISIBLE);
    expiresInText.post(new Runnable() {
      @Override
      public void run() {
        long elapsed   = System.currentTimeMillis() - messageRecord.getExpireStarted();
        long remaining = messageRecord.getExpiresIn() - elapsed;

        String duration = ExpirationUtil.getExpirationDisplayValue(MessageDetailsActivity.this, Math.max((int)(remaining / 1000), 1));
        expiresInText.setText(duration);

        if (running) {
          expiresInText.postDelayed(this, 500);
        }
      }
    });
  }

  private void updateRecipients(MessageRecord messageRecord, Recipients recipients) {
    int toFromRes = io.forsta.librelay.R.string.message_details_header__to;
    if (!messageRecord.isOutgoing()) {
      toFromRes = io.forsta.librelay.R.string.message_details_header__from;
      Recipient from = messageRecord.getIndividualRecipient();
      recipients = RecipientFactory.getRecipientsFor(MessageDetailsActivity.this, from, true);
    }
    toFrom.setText(toFromRes);
    conversationItem.bind(messageRecord, dynamicLanguage.getCurrentLocale(),
                         new HashSet<>(), recipients);
    recipientsList.setAdapter(new MessageDetailsRecipientAdapter(this, messageRecord,
                                                                 recipients, isPushGroup));
  }

  private void inflateMessageViewIfAbsent(MessageRecord messageRecord) {
    if (conversationItem == null) {
      if (messageRecord.isOutgoing()) {
        conversationItem = (ConversationItem) inflater.inflate(io.forsta.librelay.R.layout.conversation_item_sent, itemParent, false);
      } else {
        conversationItem = (ConversationItem) inflater.inflate(io.forsta.librelay.R.layout.conversation_item_received, itemParent, false);
      }
      itemParent.addView(conversationItem);
    }
  }

  private @Nullable MessageRecord getMessageRecord(Context context, Cursor cursor) {
    MessageDatabase messageDatabase = DbFactory.getMessageDatabase(context);
    MessageDatabase.Reader mmsReader = messageDatabase.readerFor(cursor);
    return mmsReader.getNext();
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new MessageDetailsLoader(this, getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    MessageRecord messageRecord = getMessageRecord(this, cursor);

    if (messageRecord == null) {
      finish();
    } else {
      new MessageRecipientAsyncTask(this, messageRecord).execute();
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    recipientsList.setAdapter(null);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  private class MessageRecipientAsyncTask extends AsyncTask<Void,Void,Recipients> {
    private WeakReference<Context> weakContext;
    private MessageRecord          messageRecord;

    public MessageRecipientAsyncTask(@NonNull  Context context, @NonNull MessageRecord messageRecord) {
      this.weakContext   = new WeakReference<>(context);
      this.messageRecord = messageRecord;
    }

    protected Context getContext() {
      return weakContext.get();
    }

    @Override
    public Recipients doInBackground(Void... voids) {
      Context context = getContext();
      if (context == null) {
        Log.w(TAG, "associated context is destroyed, finishing early");
        return null;
      }

      Recipients recipients = RecipientFactory.getRecipientsForIds(context, getIntent().getLongArrayExtra(RECIPIENTS_IDS_EXTRA), false);
      return recipients;
    }

    @Override
    public void onPostExecute(Recipients recipients) {
      if (getContext() == null) {
        Log.w(TAG, "AsyncTask finished with a destroyed context, leaving early.");
        return;
      }

      inflateMessageViewIfAbsent(messageRecord);

      updateRecipients(messageRecord, recipients);
      if (messageRecord.isFailed()) {
        errorText.setVisibility(View.VISIBLE);
        metadataContainer.setVisibility(View.GONE);
      } else {
        updateTime(messageRecord);
        updateExpirationTime(messageRecord);
        errorText.setVisibility(View.GONE);
        metadataContainer.setVisibility(View.VISIBLE);
      }
    }
  }
}
