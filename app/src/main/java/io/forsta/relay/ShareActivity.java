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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.forsta.relay.AuthenticationRequiredActionBarActivity;
import io.forsta.relay.ConversationActivity;

import io.forsta.librelay.ShareFragment;
import io.forsta.librelay.media.PartAuthority;
import io.forsta.librelay.providers.PersistentBlobProvider;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.DynamicLanguage;
import io.forsta.librelay.util.FileUtils;
import io.forsta.librelay.util.MediaUtil;
import io.forsta.librelay.util.ViewUtil;

/**
 * An activity to quickly share content with contacts
 *
 * @author Jake McGinty
 */
public class ShareActivity extends AuthenticationRequiredActionBarActivity
    implements ShareFragment.ConversationSelectedListener
{
  private static final String TAG = ShareActivity.class.getSimpleName();

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ViewGroup    fragmentContainer;
  private View         progressWheel;
  private Uri          resolvedExtra;
  private String       mimeType;
  private boolean      isPassingAlongMedia;

  @Override
  protected void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(io.forsta.librelay.R.layout.share_activity);

    fragmentContainer = ViewUtil.findById(this, io.forsta.librelay.R.id.drawer_layout);
    progressWheel = ViewUtil.findById(this, io.forsta.librelay.R.id.progress_wheel);

    initFragment(io.forsta.librelay.R.id.drawer_layout, new ShareFragment());
    initializeMedia();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    initializeMedia();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(io.forsta.librelay.R.string.ShareActivity_share_with);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!isPassingAlongMedia && resolvedExtra != null) {
      PersistentBlobProvider.getInstance(this).delete(resolvedExtra);
    }
    if (!isFinishing()) {
      finish();
    }
  }

  private void initializeMedia() {
    final Context context = this;
    isPassingAlongMedia = false;

    Uri streamExtra = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
    mimeType        = getMimeType(streamExtra);
    if (streamExtra != null && PartAuthority.isLocalUri(streamExtra)) {
      isPassingAlongMedia = true;
      resolvedExtra       = streamExtra;
      fragmentContainer.setVisibility(View.VISIBLE);
      progressWheel.setVisibility(View.GONE);
    } else {
      fragmentContainer.setVisibility(View.GONE);
      progressWheel.setVisibility(View.VISIBLE);
      new ResolveMediaTask(context).execute(streamExtra);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(io.forsta.librelay.R.menu.share, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    int i = item.getItemId();
    if (i == io.forsta.librelay.R.id.menu_new_message) {
      handleNewConversation();
      return true;
    } else if (i == android.R.id.home) {
      finish();
      return true;
    }
    return false;
  }

  private void handleNewConversation() {
    Intent intent = getBaseShareIntent(NewConversationActivity.class);
    isPassingAlongMedia = true;
    startActivity(intent);
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    createConversation(threadId, recipients, distributionType);
  }

  private void createConversation(long threadId, Recipients recipients, int distributionType) {
    final Intent intent = getBaseShareIntent(ConversationActivity.class);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    isPassingAlongMedia = true;
    startActivity(intent);
  }

  private Intent getBaseShareIntent(final @NonNull Class<?> target) {
    final Intent intent      = new Intent(this, target);
    final String textExtra   = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
    if (resolvedExtra != null) intent.setDataAndType(resolvedExtra, mimeType);

    return intent;
  }

  private String getMimeType(@Nullable Uri uri) {
    if (uri != null) {
      final String mimeType = MediaUtil.getMimeType(getApplicationContext(), uri);
      if (mimeType != null) return mimeType;
    }
    return MediaUtil.getCorrectedMimeType(getIntent().getType());
  }

  private class ResolveMediaTask extends AsyncTask<Uri, Void, Uri> {
    private final Context context;

    public ResolveMediaTask(Context context) {
      this.context = context;
    }

    @Override
    protected Uri doInBackground(Uri... uris) {
      try {
        if (uris.length != 1 || uris[0] == null) {
          return null;
        }

        InputStream inputStream;

        if ("file".equals(uris[0].getScheme())) {
          inputStream = openFileUri(uris[0]);
        } else {
          inputStream = context.getContentResolver().openInputStream(uris[0]);
        }

        if (inputStream == null) {
          return null;
        }

        return PersistentBlobProvider.getInstance(context).create(inputStream, mimeType);
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        return null;
      }
    }

    @Override
    protected void onPostExecute(Uri uri) {
      resolvedExtra = uri;
      ViewUtil.fadeIn(fragmentContainer, 300);
      ViewUtil.fadeOut(progressWheel, 300);
    }

    private InputStream openFileUri(Uri uri) throws IOException {
      FileInputStream fin   = new FileInputStream(uri.getPath());
      int             owner = FileUtils.getFileDescriptorOwner(fin.getFD());
      
      if (owner == -1 || owner == Process.myUid()) {
        fin.close();
        throw new IOException("File owned by application");
      }

      return fin;
    }
  }
}