/**
 * Copyright (C) 2013 Open Whisper Systems
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
package io.forsta.librelay.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.support.v4.content.CursorLoader;
import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.GroupDatabase;
import io.forsta.librelay.recipients.Recipient;

import java.util.List;

/**
 * CursorLoader that initializes a Contacts instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private static final String TAG = ContactsCursorLoader.class.getSimpleName();
  private final String filter;

  private final String[] columns = {
      Contacts.ID_COLUMN,
      Contacts.NAME_COLUMN,
      Contacts.NUMBER_COLUMN,
      Contacts.NUMBER_TYPE_COLUMN,
      Contacts.LABEL_COLUMN,
      Contacts.CONTACT_TYPE_COLUMN
  };

  public ContactsCursorLoader(Context context, String filter) {
    super(context);

    this.filter = filter;
  }

  @Override
  public Cursor loadInBackground() {
    MatrixCursor forstaContactsCursor = new MatrixCursor(columns, 1);
    List<Recipient> recipients = DbFactory.getContacts(getContext()).getRecipients(filter);

    try {
      for (Recipient user : recipients) {
        forstaContactsCursor.addRow(new Object[] {
            user.getRecipientId(),
            user.getName(),
            user.getAddress(),
            user.getSlug(),
            user.getOrgSlug(),
            Contacts.ATLAS_TYPE
        });
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    GroupDatabase gdb = DbFactory.getGroupDatabase(getContext());
    Cursor groupCursor = gdb.getForstaGroupsByTitle(filter);
    try {
      while (groupCursor!= null && groupCursor.moveToNext()) {
        forstaContactsCursor.addRow(new Object[] {
                groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.ID)),
                groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.TITLE)),
                groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.GROUP_ID)),
                groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.SLUG)),
                groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.ORG_SLUG)),
                Contacts.ATLAS_GROUP_TYPE
        });
      }
    } finally {
      if (groupCursor != null) {
        groupCursor.close();
      }
    }
    return forstaContactsCursor;
  }
}
