/**
 * Copyright (C) 2011 Whisper Systems
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
package io.forsta.librelay.recipients;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.BuildConfig;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class RecipientFactory {
  private static String TAG = RecipientFactory.class.getSimpleName();

  private static final RecipientProvider provider = new RecipientProvider();

  public static Recipients getRecipientsFor(Context context, Recipient recipient, boolean asynchronous) {
    long[] ids = new long[1];
    ids[0] = recipient.getRecipientId();

    return provider.getRecipients(context, ids, asynchronous);
  }

  public @NonNull static Recipient getRecipientForId(Context context, long recipientId, boolean asynchronous) {
    return provider.getRecipient(context, recipientId, asynchronous);
  }

  public @NonNull static Recipients getRecipientsForIds(Context context, long[] recipientIds, boolean asynchronous) {
    return provider.getRecipients(context, recipientIds, asynchronous);
  }

  public static @NonNull Recipients getRecipientsFromString(Context context, @NonNull String rawText, boolean asynchronous) {
    StringTokenizer tokenizer = new StringTokenizer(rawText, ",");
    List<String>    ids       = new LinkedList<>();

    while (tokenizer.hasMoreTokens()) {
      Optional<Long> id = getRecipientIdFromAddress(context, tokenizer.nextToken());

      if (id.isPresent()) {
        ids.add(String.valueOf(id.get()));
      }
    }

    return getRecipientsForIds(context, ids, asynchronous);
  }

  public static @NonNull Recipients getRecipientsFromStrings(@NonNull Context context, @NonNull List<String> numbers, boolean asynchronous) {
    List<String> ids = new LinkedList<>();

    for (String number : numbers) {
      Optional<Long> id = getRecipientIdFromAddress(context, number);

      if (id.isPresent()) {
        ids.add(String.valueOf(id.get()));
      }
    }

    return getRecipientsForIds(context, ids, asynchronous);
  }

  private static @NonNull Recipients getRecipientsForIds(Context context, List<String> idStrings, boolean asynchronous) {
    long[]       ids      = new long[idStrings.size()];
    int          i        = 0;

    for (String id : idStrings) {
      ids[i++] = Long.parseLong(id);
    }

    return provider.getRecipients(context, ids, asynchronous);
  }

  private static Optional<Long> getRecipientIdFromAddress(Context context, String address) {
    address = address.trim();

    if (address.isEmpty()) return Optional.absent();

    return Optional.of(DbFactory.getContacts(context).getIdFromAddress(address));
  }
  
  public static Recipient getRecipient(Context context, String uid, boolean async) {
    long id = getRecipientIdFromAddress(context, uid).get();
    return provider.getRecipient(context, id, async);
  }

  public static void clearCache(Context context) {
    Log.w(TAG, "clearCache called. Sending broadcast: RECIPIENT_CLEAR_ACTION ");
    provider.clearCache();
    context.sendBroadcast(new Intent(Recipients.RECIPIENT_CLEAR_ACTION));
  }
}
