package io.forsta.librelay.media;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.module.GlideModule;

import io.forsta.librelay.media.AttachmentStreamUriLoader.AttachmentModel;
import io.forsta.librelay.media.ContactPhotoUriLoader.ContactPhotoUri;

import java.io.InputStream;

public class TextSecureGlideModule implements GlideModule {
  @Override
  public void applyOptions(Context context, GlideBuilder builder) {
    builder.setDiskCache(new NoopDiskCacheFactory());
  }

  @Override
  public void registerComponents(Context context, Glide glide) {
    glide.register(MediaStreamUriLoader.AttachmentUri.class, InputStream.class, new MediaStreamUriLoader.Factory());
    glide.register(ContactPhotoUri.class, InputStream.class, new ContactPhotoUriLoader.Factory());
    glide.register(AttachmentModel.class, InputStream.class, new AttachmentStreamUriLoader.Factory());
  }

  public static class NoopDiskCacheFactory implements DiskCache.Factory {
    @Override
    public DiskCache build() {
      return new DiskCacheAdapter();
    }
  }
}
