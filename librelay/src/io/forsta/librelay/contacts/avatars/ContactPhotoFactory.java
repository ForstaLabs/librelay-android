package io.forsta.librelay.contacts.avatars;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.bumptech.glide.Glide;

import io.forsta.librelay.R;
import io.forsta.librelay.media.ContactPhotoUriLoader.ContactPhotoUri;

import java.util.concurrent.ExecutionException;

public class ContactPhotoFactory {

  private static final String TAG = ContactPhotoFactory.class.getSimpleName();
  
  public static ContactPhoto getLoadingPhoto() {
    return new TransparentContactPhoto();
  }

  public static ContactPhoto getDefaultContactPhoto(@Nullable String name) {
    if (!TextUtils.isEmpty(name)) return new GeneratedContactPhoto(name);
    else                          return new GeneratedContactPhoto("#");
  }

  public static ContactPhoto getResourceContactPhoto(@DrawableRes int resourceId) {
    return new ResourceContactPhoto(resourceId);
  }

  public static ContactPhoto getDefaultGroupPhoto() {
    return new ResourceContactPhoto(R.drawable.ic_group_white_24dp);
  }

  public static ContactPhoto getContactPhoto(Context context, Uri uri, String name) {
    int targetSize = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);
    return getContactPhoto(context, uri, name, targetSize);
  }

  public static ContactPhoto getContactPhoto(@NonNull  Context context,
                                             @Nullable Uri uri,
                                             @Nullable String name,
                                             int targetSize)
  {
    if (uri == null) return getDefaultContactPhoto(name);

    try {
      Bitmap bitmap = Glide.with(context)
                           .load(new ContactPhotoUri(uri)).asBitmap()
                           .centerCrop().into(targetSize, targetSize).get();
      return new BitmapContactPhoto(bitmap);
    } catch (ExecutionException e) {
      return getDefaultContactPhoto(name);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public static ContactPhoto getGroupContactPhoto(@Nullable byte[] avatar) {
    if (avatar == null) return getDefaultGroupPhoto();

    return new BitmapContactPhoto(BitmapFactory.decodeByteArray(avatar, 0, avatar.length));
  }
}
