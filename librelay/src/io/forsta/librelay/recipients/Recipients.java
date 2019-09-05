/**
 * Copyright (C) 2015 Open Whisper Systems
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.contacts.avatars.ContactColors;
import io.forsta.librelay.contacts.avatars.ContactPhoto;
import io.forsta.librelay.contacts.avatars.ContactPhotoFactory;
import io.forsta.librelay.recipients.Recipient.RecipientModifiedListener;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

public class Recipients implements Iterable<Recipient>, RecipientModifiedListener {
  private static final String TAG = Recipients.class.getSimpleName();
  public static final String RECIPIENT_CLEAR_ACTION = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".Recipients.CLEAR";

  private final Set<RecipientsModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientsModifiedListener, Boolean>());
  private final List<Recipient> recipients;

  Recipients(List<Recipient> recipients) {
    this.recipients = recipients;
  }

  public @NonNull
  ContactPhoto getContactPhoto() {
    if (recipients.size() == 1) return recipients.get(0).getContactPhoto();
    else                        return ContactPhotoFactory.getDefaultGroupPhoto();
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (!isSingleRecipient()) return MaterialColor.GROUP;
    else if (isEmpty())                                  return ContactColors.UNKNOWN_COLOR;
    else                                                 return recipients.get(0).getColor();
  }

  public synchronized void addListener(RecipientsModifiedListener listener) {
    if (listeners.isEmpty()) {
      for (Recipient recipient : recipients) {
        recipient.addListener(this);
      }
    }

    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientsModifiedListener listener) {
    listeners.remove(listener);

    if (listeners.isEmpty()) {
      for (Recipient recipient : recipients) {
        recipient.removeListener(this);
      }
    }
  }

  public boolean includesSelf(Context context) {
    for (Recipient recipient: recipients) {
      if (recipient.getAddress().equals(TextSecurePreferences.getLocalAddress(context))) {
        return true;
      }
    }
    return false;
  }

  public String getRecipientExpression() {
    StringBuilder sb = new StringBuilder();
    for (Recipient recipient : recipients) {
      sb.append(recipient.getFullTag()).append(" ");
    }
    return sb.toString();
  }

  public String getLocalizedRecipientExpression(String orgTag) {
    StringBuilder sb = new StringBuilder();
    for (Recipient recipient : recipients) {
      if (recipient.getOrgSlug().equals(orgTag)) {
        sb.append(recipient.getLocalTag()).append(" ");
      } else {
        sb.append(recipient.getFullTag()).append(" ");
      }
    }
    return sb.toString();
  }

  public boolean isEmpty() {
    return this.recipients.isEmpty();
  }

  public boolean isSingleRecipient() {
    return this.recipients.size() == 1;
  }

  public @Nullable Recipient getPrimaryRecipient() {
    if (!isEmpty())
      return this.recipients.get(0);
    else
      return null;
  }

  public Recipient getRecipient(String address) {
    for (Recipient recipient : recipients) {
      if (recipient.getAddress().equals(address)) {
        return recipient;
      }
    }
    return null;
  }

  public List<Recipient> getRecipientsList() {
    return this.recipients;
  }

  public long[] getIds() {
    long[] ids = new long[recipients.size()];
    for (int i=0; i<recipients.size(); i++) {
      ids[i] = recipients.get(i).getRecipientId();
    }
    return ids;
  }

  public List<String> getAddresses() {
    List<String> addresses = new ArrayList<>();
    for (Recipient recipient : recipients) {
      addresses.add(recipient.getAddress());
    }
    return addresses;
  }

  public String getSortedAddresses() {
    TreeSet<String> addresses = new TreeSet<>();
    for (Recipient recipient : recipients) {
      addresses.add(recipient.getAddress());
    }
    return Util.join(addresses.toArray(new String[addresses.size()]), ",");
  }

  public List<String> toStringList() {
    return toNumberStringList(false);
  }

  public @NonNull List<String> toNumberStringList(boolean scrub) {
    List<String> results = new LinkedList<>();
    for (Recipient recipient : recipients) {
      results.add(recipient.getAddress());
    }

    return results;
  }

  public String toCondensedString(Context context) {
    StringBuilder sb = new StringBuilder();
    List<String> addresses = new ArrayList<>();

    if (recipients.size() == 1) {
      String name = !TextUtils.isEmpty(getPrimaryRecipient().getName()) ? getPrimaryRecipient().getName() : "Unknown Recipient";
      if (!getPrimaryRecipient().isActive()) {
        name += " (Removed User)";
      }
      return name;
    }

    for (int i=0; i<recipients.size(); i++) {
      String address = recipients.get(i).getAddress();
      if (!address.equals(TextSecurePreferences.getLocalAddress(context))) {
        String name = !TextUtils.isEmpty(recipients.get(i).getName()) ? recipients.get(i).getName() : "Unknown Recipient";
        if (!recipients.get(i).isActive()) {
          name += " (Removed User)";
        }
        addresses.add(name);
      }
    }

    if(addresses.size() > 2) {
      return addresses.get(0) + " and " + addresses.size() + " others";
    } else {
      if (addresses.size() == 0) {
        return "Unknown Recipient";
      }
      return TextUtils.join(", ", addresses);
    }
  }

  public String toShortString() {
    String fromString = "";

    for (int i=0;i<recipients.size();i++) {

      fromString += recipients.get(i).toShortString();

      if (i != recipients.size() -1 )
        fromString += ", ";
    }

    return fromString;
  }

  public String toFullString() {
    StringBuilder sb = new StringBuilder();
    for (Recipient recipient : recipients) {
      sb.append(recipient.getFullTag()).append(" (");
      sb.append(recipient.getAddress()).append(") ");
    }
    return sb.toString();
  }

  @Override
  public Iterator<Recipient> iterator() {
    return recipients.iterator();
  }

  @Override
  public void onModified(Recipient recipient) {
    notifyListeners();
  }

  private void notifyListeners() {
    Set<RecipientsModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientsModifiedListener listener : localListeners) {
      listener.onModified(this);
    }
  }

  public interface RecipientsModifiedListener {
    void onModified(Recipients recipients);
  }
}
