package io.forsta.librelay.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.librelay.crypto.MediaKey;
import io.forsta.librelay.database.AttachmentDatabase;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class PointerAttachment extends Attachment {

  public PointerAttachment(@NonNull String contentType, int transferState, long size,
                           @NonNull String location, @NonNull String key, @NonNull String relay)
  {
    super(contentType, transferState, size, location, key, relay);
  }

  @Nullable
  @Override
  public Uri getDataUri() {
    return null;
  }

  @Nullable
  @Override
  public Uri getThumbnailUri() {
    return null;
  }


  public static List<Attachment> forPointers(Optional<List<SignalServiceAttachment>> pointers) {
    List<Attachment> results = new LinkedList<>();

    if (pointers.isPresent()) {
      for (SignalServiceAttachment pointer : pointers.get()) {
        if (pointer.isPointer()) {
          String encryptedKey = MediaKey.getEncoded(pointer.asPointer().getKey());
          results.add(new PointerAttachment(pointer.getContentType(),
                                            AttachmentDatabase.TRANSFER_PROGRESS_AUTO_PENDING,
                                            pointer.asPointer().getSize().or(0),
                                            String.valueOf(pointer.asPointer().getId()),
                                            encryptedKey, pointer.asPointer().getRelay().orNull()));
        }
      }
    }

    return results;
  }
}
