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

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;

import io.forsta.librelay.components.ZoomingImageView;
import io.forsta.librelay.media.VideoSlide;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.util.DateUtils;
import io.forsta.librelay.util.DynamicLanguage;
import io.forsta.librelay.util.SaveAttachmentTask;
import io.forsta.librelay.video.VideoPlayer;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaPreviewActivity extends AuthenticationRequiredActionBarActivity implements Recipient.RecipientModifiedListener {
  private final static String TAG = MediaPreviewActivity.class.getSimpleName();

  public static final String RECIPIENT_EXTRA = "recipient";
  public static final String DATE_EXTRA      = "date";
  public static final String SIZE_EXTRA      = "size";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ZoomingImageView  image;
  private VideoPlayer video;
  private Uri               mediaUri;
  private String            mediaType;
  private Recipient         recipient;
  private long              date;
  private long              size;

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    dynamicLanguage.onCreate(this);

    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.media_preview_activity);

    initializeViews();
    initializeResources();
    initializeActionBar();
  }

  @Override
  public void onModified(Recipient recipient) {
    initializeActionBar();
  }

  private void initializeActionBar() {
    final CharSequence relativeTimeSpan;
    if (date > 0) {
      relativeTimeSpan = DateUtils.getRelativeTimeSpanString(date,
          System.currentTimeMillis(),
          DateUtils.MINUTE_IN_MILLIS);
    } else {
      relativeTimeSpan = null;
    }
    getSupportActionBar().setTitle(recipient == null ? getString(R.string.MediaPreviewActivity_you)
        : recipient.toShortString());
    getSupportActionBar().setSubtitle(relativeTimeSpan);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
    if (recipient != null) recipient.addListener(this);
    initializeMedia();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (recipient != null) recipient.removeListener(this);
    cleanupMedia();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (recipient != null) recipient.removeListener(this);
    setIntent(intent);
    initializeResources();
    initializeActionBar();
  }

  private void initializeViews() {
    image = (ZoomingImageView)findViewById(io.forsta.librelay.R.id.image);
    video = (VideoPlayer)findViewById(R.id.video_player);
  }

  private void initializeResources() {
    final long recipientId = getIntent().getLongExtra(RECIPIENT_EXTRA, -1);

    mediaUri     = getIntent().getData();
    mediaType    = getIntent().getType();
    date         = getIntent().getLongExtra(DATE_EXTRA, System.currentTimeMillis());
    size         = getIntent().getLongExtra(SIZE_EXTRA, 0);

    if (recipientId > -1) {
      recipient = RecipientFactory.getRecipientForId(this, recipientId, true);
      recipient.addListener(this);
    } else {
      recipient = null;
    }
  }

  private void initializeMedia() {
    if (!isContentTypeSupported(mediaType)) {
      Log.w(TAG, "Unsupported media type sent to MediaPreviewActivity, finishing.");
      Toast.makeText(getApplicationContext(), io.forsta.librelay.R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      finish();
    }

    Log.w(TAG, "Loading Part URI: " + mediaUri);

    try {
      if (mediaType != null && mediaType.startsWith("image/")) {
        image.setVisibility(View.VISIBLE);
        video.setVisibility(View.GONE);
        image.setImageUri(mediaUri);
      } else if (mediaType != null && mediaType.startsWith("video/")) {
        image.setVisibility(View.GONE);
        video.setVisibility(View.VISIBLE);
        video.setVideoSource(new VideoSlide(this, mediaUri, size));
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      Toast.makeText(getApplicationContext(), io.forsta.librelay.R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      finish();
    }
  }

  private void cleanupMedia() {
    image.setImageDrawable(null);
    video.cleanup();
  }

  private void forward() {
    Intent composeIntent = new Intent(this, ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
    composeIntent.setType(mediaType);
    startActivity(composeIntent);
  }

  private void saveToDisk() {
    SaveAttachmentTask.showWarningDialog(this, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        SaveAttachmentTask saveTask = new SaveAttachmentTask(MediaPreviewActivity.this);
        saveTask.execute(new SaveAttachmentTask.Attachment(mediaUri, mediaType, date));
      }
    });
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(io.forsta.librelay.R.menu.media_preview, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int i = item.getItemId();
    if (i == io.forsta.librelay.R.id.media_preview__forward) {
      forward();
      return true;
    } else if (i == io.forsta.librelay.R.id.save) {
      saveToDisk();
      return true;
    } else if (i == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  public static boolean isContentTypeSupported(final String contentType) {
    return contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"));
  }
}
