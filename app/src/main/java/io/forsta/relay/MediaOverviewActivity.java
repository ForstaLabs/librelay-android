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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.forsta.librelay.recipients.Recipients;
import io.forsta.relay.AuthenticationRequiredActionBarActivity;

import io.forsta.librelay.ImageMediaAdapter;
import io.forsta.librelay.database.CursorRecyclerViewAdapter;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.ImageDatabase.ImageRecord;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipient.RecipientModifiedListener;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.util.AbstractCursorLoader;
import io.forsta.librelay.util.DynamicLanguage;
import io.forsta.librelay.util.SaveAttachmentTask;
import io.forsta.librelay.util.task.ProgressDialogAsyncTask;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaOverviewActivity extends AuthenticationRequiredActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor>, ImageMediaAdapter.MediaClickListener {
  private final static String TAG = MediaOverviewActivity.class.getSimpleName();

  public static final String RECIPIENT_EXTRA = "recipient";
  public static final String THREAD_ID_EXTRA = "thread_id";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private RecyclerView      gridView;
  private GridLayoutManager gridManager;
  private TextView          noImages;
  private Recipient         recipient;
  private long              threadId;

  @Override
  protected void onPreCreate() {
    this.setTheme(io.forsta.librelay.R.style.TextSecure_DarkTheme);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setFullscreenIfPossible();

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_overview_activity);

    initializeResources();
    initializeActionBar();
    getSupportLoaderManager().initLoader(0, null, MediaOverviewActivity.this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (gridManager != null) gridManager.setSpanCount(getResources().getInteger(R.integer.media_overview_cols));
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN)
  private void setFullscreenIfPossible() {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  private void initializeActionBar() {
    getSupportActionBar().setTitle(recipient == null
                                   ? getString(io.forsta.librelay.R.string.AndroidManifest__media_overview)
                                   : getString(io.forsta.librelay.R.string.AndroidManifest__media_overview_named, recipient.toShortString()));
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  private void initializeResources() {
    threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);

    noImages = (TextView    ) findViewById(R.id.no_images );
    gridView = (RecyclerView) findViewById(R.id.media_grid);
    gridManager = new GridLayoutManager(this, getResources().getInteger(io.forsta.librelay.R.integer.media_overview_cols));
    gridView.setLayoutManager(gridManager);
    gridView.setHasFixedSize(true);

    final long recipientId = getIntent().getLongExtra(RECIPIENT_EXTRA, -1);
    if (recipientId > -1) {
      recipient = RecipientFactory.getRecipientForId(this, recipientId, true);
      recipient.addListener(new RecipientModifiedListener() {
        @Override
        public void onModified(Recipient recipient) {
          initializeActionBar();
        }
      });
    } else {
      recipient = null;
    }
  }

  private void saveToDisk() {
    final Context c = this;

    SaveAttachmentTask.showWarningDialog(this, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        new ProgressDialogAsyncTask<Void, Void, List<SaveAttachmentTask.Attachment>>(c,
                                                                                     io.forsta.librelay.R.string.ConversationFragment_collecting_attahments,
                                                                                     io.forsta.librelay.R.string.please_wait) {
          @Override
          protected List<SaveAttachmentTask.Attachment> doInBackground(Void... params) {
            Cursor cursor                                   = DbFactory.getImageDatabase(c).getImagesForThread(threadId);
            List<SaveAttachmentTask.Attachment> attachments = new ArrayList<>(cursor.getCount());

            while (cursor != null && cursor.moveToNext()) {
              ImageRecord record = ImageRecord.from(cursor);
              attachments.add(new SaveAttachmentTask.Attachment(record.getAttachment().getDataUri(),
                                                                record.getContentType(),
                                                                record.getDate()));
            }

            return attachments;
          }

          @Override
          protected void onPostExecute(List<SaveAttachmentTask.Attachment> attachments) {
            super.onPostExecute(attachments);

            SaveAttachmentTask saveTask = new SaveAttachmentTask(c, attachments.size());
            saveTask.execute(attachments.toArray(new SaveAttachmentTask.Attachment[attachments.size()]));
          }
        }.execute();
      }
    }, gridView.getAdapter().getItemCount());
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    if (gridView.getAdapter() != null && gridView.getAdapter().getItemCount() > 0) {
      MenuInflater inflater = this.getMenuInflater();
      inflater.inflate(io.forsta.librelay.R.menu.media_overview, menu);
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int i = item.getItemId();
    if (i == io.forsta.librelay.R.id.save) {
      saveToDisk();
      return true;
    } else if (i == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    return new ThreadMediaLoader(this, threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    Log.w(TAG, "onLoadFinished()");
    gridView.setAdapter(new ImageMediaAdapter(this, cursor, this));
    noImages.setVisibility(gridView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
    invalidateOptionsMenu();
  }

  @Override
  public void onLoaderReset(Loader<Cursor> cursorLoader) {
    ((CursorRecyclerViewAdapter)gridView.getAdapter()).changeCursor(null);
  }

  @Override
  public void onClick(ImageRecord imageRecord) {
      Intent intent = new Intent(this, MediaPreviewActivity.class);
      intent.putExtra(MediaPreviewActivity.DATE_EXTRA, imageRecord.getDate());

      if (!TextUtils.isEmpty(imageRecord.getAddress())) {
        Recipients recipients = RecipientFactory.getRecipientsFromString(this,
                                                                         imageRecord.getAddress(),
                                                                         true);
        if (recipients != null && recipients.getPrimaryRecipient() != null) {
          intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
        }
      }
      intent.setDataAndType(imageRecord.getAttachment().getDataUri(), imageRecord.getContentType());
      startActivity(intent);
  }

  public static class ThreadMediaLoader extends AbstractCursorLoader {
    private final long threadId;

    public ThreadMediaLoader(Context context, long threadId) {
      super(context);
      this.threadId = threadId;
    }

    @Override
    public Cursor getCursor() {
      return DbFactory.getImageDatabase(getContext()).getImagesForThread(threadId);
    }
  }
}
