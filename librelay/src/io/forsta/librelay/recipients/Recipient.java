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

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.contacts.avatars.ContactColors;
import io.forsta.librelay.contacts.avatars.ContactPhoto;
import io.forsta.librelay.contacts.avatars.ContactPhotoFactory;
import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.util.FutureTaskListener;
import io.forsta.librelay.util.ListenableFutureTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

public class Recipient {
  private final static String TAG = Recipient.class.getSimpleName();
  private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());
  private @NonNull long recipientId;
  private @NonNull  String address;
  private @Nullable String name;
  private @Nullable String username;
  private  @Nullable String tag_id;
  private @Nullable String slug;
  private  @Nullable String org_id;
  private @Nullable String orgSlug;
  private @Nullable String email;
  private @Nullable String phone;
  private @Nullable String gravatarHash;
  private ContactPhoto contactPhoto;
  private boolean isActive = true;
  public boolean isMonitor = false;
  private boolean stale = false;
  private String userType;
  private Uri contactUri;

  @Nullable private MaterialColor color;

  public enum RecipientType {
    PERSON,
    BOT,
    EPHEMERAL,
    UNKNOWN
  }

  Recipient(@NonNull ListenableFutureTask<Recipient> future) {
    future.addListener(new FutureTaskListener<Recipient>() {
      @Override
      public void onSuccess(Recipient result) {
        if (result != null) {
          synchronized (Recipient.this) {
            Recipient.this.recipientId = result.recipientId;
            Recipient.this.name         = result.name;
            Recipient.this.username = result.username;
            Recipient.this.address = result.address;
            Recipient.this.contactUri   = result.contactUri;
            Recipient.this.contactPhoto = ContactPhotoFactory.getDefaultContactPhoto(result.name);
            Recipient.this.gravatarHash = result.gravatarHash;
            Recipient.this.color        = result.color;
            Recipient.this.slug = result.slug;
            Recipient.this.orgSlug = result.orgSlug;
            Recipient.this.email = result.email;
            Recipient.this.phone = result.phone;
            Recipient.this.isActive = result.isActive;
            Recipient.this.userType = result.userType;
          }

          notifyListeners();
        }
      }

      @Override
      public void onFailure(ExecutionException error) {
        Log.w(TAG, error);
      }
    });
  }

  public Recipient(long recipientId, String address, String name, String slug, String orgSlug, String email, String phone, String userType, String gravatarHash) {
    this.recipientId = recipientId;
    this.address = address;
    this.name = name;
    this.slug = slug;
    this.orgSlug = orgSlug;
    this.email = email;
    this.phone = phone;
    this.userType = userType;
    this.contactPhoto = ContactPhotoFactory.getDefaultContactPhoto(name);
    this.gravatarHash = gravatarHash;
  }

  public Recipient(Cursor cursor) {
    this.recipientId = cursor.getLong(cursor.getColumnIndex(Contacts.ID));
    this.address = cursor.getString(cursor.getColumnIndex(Contacts.ADDRESS));
    this.org_id = cursor.getString(cursor.getColumnIndex(Contacts.ORGID));
    this.gravatarHash = cursor.getString(cursor.getColumnIndex(Contacts.AVATAR));
    this.orgSlug = cursor.getString(cursor.getColumnIndex(Contacts.ORGSLUG));
    this.tag_id = cursor.getString(cursor.getColumnIndex(Contacts.TAGID));
    this.slug = cursor.getString(cursor.getColumnIndex(Contacts.SLUG));
    this.username = cursor.getString(cursor.getColumnIndex(Contacts.USERNAME));
    this.name = cursor.getString(cursor.getColumnIndex(Contacts.NAME));
    this.email = cursor.getString(cursor.getColumnIndex(Contacts.EMAIL));
    this.phone = cursor.getString(cursor.getColumnIndex(Contacts.NUMBER));
  }

  public synchronized RecipientType getRecipientType() {
    switch (this.userType) {
      case "PERSON":
        return RecipientType.PERSON;
      case "BOT":
        return RecipientType.BOT;
      case "EPHEMERAL":
        return RecipientType.EPHEMERAL;
    }
    return RecipientType.UNKNOWN;
  }

  public synchronized @NonNull String getAddress() {
    return address;
  }

  public synchronized boolean isBlocked() {
    return false;
  }

  public synchronized boolean isMuted() {
    return false;
  }

  public synchronized @Nullable Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized @Nullable String getName() {
    return this.name;
  }

  public synchronized @Nullable String getSlug() {
    return this.slug;
  }

  public synchronized @Nullable String getOrgSlug() {
    return this.orgSlug;
  }

  public synchronized boolean isActive() {
    return this.isActive;
  }

  public synchronized String getUserType() {
    return this.userType;
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (color != null) return color;
    else if (name != null)  return ContactColors.generateFor(name);
    else                    return ContactColors.UNKNOWN_COLOR;
  }

  public void setColor(@NonNull MaterialColor color) {
    synchronized (this) {
      this.color = color;
    }

    notifyListeners();
  }

  public synchronized String getPhone() {
    return phone;
  }

  public synchronized String getEmail() {
    return email;
  }

  public synchronized String getFullTag() {
    return "@" + slug + ":" + orgSlug;
  }

  public synchronized String getLocalTag() {
    return "@" + slug;
  }

  public synchronized long getRecipientId() {
    return recipientId;
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);
  }

  public synchronized String toShortString() {
    String nameString = name != null ? name : "Unknown Recipient";
    if (!this.isActive) {
      nameString += " (Removed User)";
    }
    return nameString;
  }

  public synchronized @NonNull ContactPhoto getContactPhoto() {
    return contactPhoto;
  }

  @Override
  public String toString() {
    return "" + this.getFullTag() + " (" + this.name + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof Recipient)) return false;

    Recipient that = (Recipient) o;

    return this.recipientId == that.recipientId;
  }

  @Override
  public int hashCode() {
    return 31 + (int)this.recipientId;
  }

  private void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners)
      listener.onModified(this);
  }

  public interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }

  boolean isStale() {
    return stale;
  }

  void setStale() {
    this.stale = true;
  }

  public String getGravitarUrl() {
    if (!TextUtils.isEmpty(gravatarHash)) {
      return "https://www.gravatar.com/avatar/" + gravatarHash + "?default=404";
    }
    return null;
  }
}
