package io.forsta.librelay.media;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;

import com.bumptech.glide.load.data.StreamLocalUriFetcher;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class ContactPhotoLocalUriFetcher extends StreamLocalUriFetcher {
  private static final String TAG = ContactPhotoLocalUriFetcher.class.getSimpleName();

  public ContactPhotoLocalUriFetcher(Context context, Uri uri) {
    super(context, uri);
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver)
      throws FileNotFoundException
  {
    return ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, true);
  }
}
