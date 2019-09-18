package io.forsta.librelay.media;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import io.forsta.librelay.attachments.Attachment;
import io.forsta.librelay.util.BitmapDecodingException;
import io.forsta.librelay.util.BitmapUtil;
import io.forsta.librelay.util.MediaUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import ws.com.google.android.mms.ContentType;

public abstract class MediaConstraints {
  private static final String TAG = MediaConstraints.class.getSimpleName();

  public static MediaConstraints PUSH_CONSTRAINTS = new PushMediaConstraints();

  public abstract int getImageMaxWidth(Context context);
  public abstract int getImageMaxHeight(Context context);
  public abstract int getImageMaxSize();

  public abstract int getGifMaxSize();

  public abstract int getVideoMaxSize();

  public abstract int getAudioMaxSize();

  public boolean isSatisfied(@NonNull Context context, @NonNull Attachment attachment) {
    try {
      return (MediaUtil.isGif(attachment)    && attachment.getSize() <= getGifMaxSize()   && isWithinBounds(context, attachment.getDataUri())) ||
             (MediaUtil.isImage(attachment)  && attachment.getSize() <= getImageMaxSize() && isWithinBounds(context, attachment.getDataUri())) ||
             (MediaUtil.isAudio(attachment)  && attachment.getSize() <= getAudioMaxSize()) ||
             (MediaUtil.isVideo(attachment)  && attachment.getSize() <= getVideoMaxSize()) ||
             (!MediaUtil.isImage(attachment) && !MediaUtil.isAudio(attachment) && !MediaUtil.isVideo(attachment));
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  public boolean isWithinBounds(Context context, Uri uri) throws IOException {
    try {
      InputStream is = PartAuthority.getAttachmentStream(context, uri);
      Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
      return dimensions.first  > 0 && dimensions.first  <= getImageMaxWidth(context) &&
             dimensions.second > 0 && dimensions.second <= getImageMaxHeight(context);
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public boolean canResize(@Nullable Attachment attachment) {
    return attachment != null && MediaUtil.isImage(attachment) && !MediaUtil.isGif(attachment);
  }

  public MediaStream getResizedMedia(@NonNull Context context,
                                     @NonNull Attachment attachment)
      throws IOException
  {
    if (!canResize(attachment)) {
      throw new UnsupportedOperationException("Cannot resize this content type");
    }

    try {
      // XXX - This is loading everything into memory! We want the send path to be stream-like.
      return new MediaStream(new ByteArrayInputStream(BitmapUtil.createScaledBytes(context, new MediaStreamUriLoader.AttachmentUri(attachment.getDataUri()), this)),
                             ContentType.IMAGE_JPEG);
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }
}
