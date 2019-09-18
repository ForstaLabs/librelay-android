package io.forsta.librelay.media;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.bumptech.glide.load.data.StreamLocalUriFetcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class MediaStreamLocalUriFetcher extends StreamLocalUriFetcher {

  private static final String TAG = MediaStreamLocalUriFetcher.class.getSimpleName();

  private Context      context;

  public MediaStreamLocalUriFetcher(Context context, Uri uri) {
    super(context, uri);
    this.context      = context;
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver) throws FileNotFoundException {
    try {
      return PartAuthority.getAttachmentStream(context, uri);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new FileNotFoundException("PartAuthority couldn't load Uri resource.");
    }
  }
}
