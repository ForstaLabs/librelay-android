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
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.util.LRUCache;
import io.forsta.librelay.util.ListenableFutureTask;
import io.forsta.librelay.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import io.forsta.librelay.database.RecipientPreferenceDatabase;

public class RecipientProvider {

  private static final String TAG = RecipientProvider.class.getSimpleName();

  private static final RecipientCache  recipientCache         = new RecipientCache();
  private static final ExecutorService asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  @NonNull Recipient getRecipient(Context context, long recipientId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(recipientId);
    if (cachedRecipient != null && !cachedRecipient.isStale() && !TextUtils.isEmpty(cachedRecipient.getSlug())) {
      return cachedRecipient;
    }

    String address = DbFactory.getContacts(context).getAddressFromId(recipientId);

    if (asynchronous) {
      cachedRecipient = new Recipient(getRecipientDetailsAsync(context, recipientId, address));
    } else {
      cachedRecipient = getRecipientDetailsSync(context, recipientId, address);
    }

    if (!TextUtils.isEmpty(cachedRecipient.getSlug())) {
      // Don't cache recipients with only address in db. Most likely because directory has been reset.
      recipientCache.set(recipientId, cachedRecipient);
    }
    return cachedRecipient;
  }

  @NonNull Recipients getRecipients(Context context, long[] recipientIds, boolean asynchronous) {

    List<Recipient> recipientList = new LinkedList<>();
    for (long recipientId : recipientIds) {
      recipientList.add(getRecipient(context, recipientId, asynchronous));
    }

    return new Recipients(recipientList);
  }

  void clearCache() {
    recipientCache.reset();
  }

  private @NonNull
  ListenableFutureTask<Recipient> getRecipientDetailsAsync(final Context context,
                                                                  final long recipientId,
                                                                  final @NonNull String address)
  {
    Callable<Recipient> task = new Callable<Recipient>() {
      @Override
      public Recipient call() throws Exception {
        return getRecipientDetailsSync(context, recipientId, address);
      }
    };

    ListenableFutureTask<Recipient> future = new ListenableFutureTask<>(task);
    asyncRecipientResolver.submit(future);
    return future;
  }

  private @NonNull Recipient getRecipientDetailsSync(Context context, long recipientId, @NonNull String address) {
    return getIndividualRecipientDetails(context, recipientId, address);
  }

  private @NonNull Recipient getIndividualRecipientDetails(Context context, long recipientId, @NonNull String address) {
    Log.w(TAG, "Getting recipient details:" + recipientId + " address: " + address);
    Optional<RecipientPreferenceDatabase.RecipientPreferences> preferences = DbFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(new long[]{recipientId});
    MaterialColor color = preferences.isPresent() ? preferences.get().getColor() : null;

    Contacts db = DbFactory.getContacts(context);
    Cursor cursor = null;
    try {
      cursor  = db.getContactByAddress(address);
      if (cursor != null && cursor.moveToFirst()) {
        return new Recipient(cursor);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (cursor != null)
        cursor.close();
    }

    // If user is not in table, insert address and return unknown. Need to now fetch new
    long id = db.getIdFromAddress(address);
    List<String> addresses = new ArrayList<>();
    addresses.add(address);
    DirectoryHelper.refreshDirectoryFor(context, addresses);
    return new Recipient(id, address, "Unknown Recipient", "", "", "", "", AtlasUser.RecipientType.PERSON.toString(), "");
  }

  private static class RecipientCache {

    private final Map<Long,Recipient> cache = new LRUCache<>(1000);

    public synchronized Recipient get(long recipientId) {
      return cache.get(recipientId);
    }

    public synchronized void set(long recipientId, Recipient recipient) {
      cache.put(recipientId, recipient);
    }

    public synchronized void reset() {
      for (Recipient recipient : cache.values()) {
        recipient.setStale();
      }
    }
  }
}