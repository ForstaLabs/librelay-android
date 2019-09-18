package io.forsta.librelay.media;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import io.forsta.librelay.media.MediaStreamUriLoader.AttachmentUri;

import java.io.InputStream;

/**
 * A {@link ModelLoader} for translating uri models into {@link InputStream} data. Capable of handling 'http',
 * 'https', 'android.resource', 'content', and 'file' schemes. Unsupported schemes will throw an exception in
 * {@link #getResourceFetcher(Uri, int, int)}.
 */
public class MediaStreamUriLoader implements StreamModelLoader<AttachmentUri> {
  private final Context context;

  /**
   * THe default factory for {@link com.bumptech.glide.load.model.stream.StreamUriLoader}s.
   */
  public static class Factory implements ModelLoaderFactory<AttachmentUri, InputStream> {

    @Override
    public StreamModelLoader<AttachmentUri> build(Context context, GenericLoaderFactory factories) {
      return new MediaStreamUriLoader(context);
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public MediaStreamUriLoader(Context context) {
    this.context = context;
  }

  @Override
  public DataFetcher<InputStream> getResourceFetcher(AttachmentUri model, int width, int height) {
    return new MediaStreamLocalUriFetcher(context, model.uri);
  }

  public static class AttachmentUri {
    public @NonNull Uri          uri;

    public AttachmentUri(@NonNull Uri uri) {
      this.uri          = uri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AttachmentUri that = (AttachmentUri)o;

      return uri.equals(that.uri);

    }

    @Override
    public int hashCode() {
      return uri.hashCode();
    }
  }
}

