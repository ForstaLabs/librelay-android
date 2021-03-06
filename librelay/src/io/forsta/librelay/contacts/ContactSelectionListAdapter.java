/**
 * Copyright (C) 2014 Open Whisper Systems
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
import android.content.res.TypedArray;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.R;
import io.forsta.librelay.components.RecyclerViewFastScroller.FastScrollAdapter;
import io.forsta.librelay.contacts.ContactSelectionListFragment.StickyHeaderAdapter;
import io.forsta.librelay.contacts.ContactSelectionListAdapter.HeaderViewHolder;
import io.forsta.librelay.contacts.ContactSelectionListAdapter.ViewHolder;
import io.forsta.librelay.database.CursorRecyclerViewAdapter;
import io.forsta.librelay.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * List adapter to display all contacts and their related information
 *
 * @author Jake McGinty
 */
public class ContactSelectionListAdapter extends CursorRecyclerViewAdapter<ViewHolder>
                                         implements FastScrollAdapter,
                                                    StickyHeaderAdapter<HeaderViewHolder>
{
  private final static String TAG = ContactSelectionListAdapter.class.getSimpleName();

  private final static int STYLE_ATTRIBUTES[] = new int[]{R.attr.contact_selection_push_user,
                                                          R.attr.contact_selection_lay_user};

  private final boolean           multiSelect;
  private final LayoutInflater    li;
  private final TypedArray        drawables;
  private final ItemClickListener clickListener;
  private final ItemLongClickListener longClickListener;

  private final HashMap<Long, String> selectedContacts = new HashMap<>();
  private final Set<String> selectedAddresses = new HashSet();

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(@NonNull  final View              itemView,
                      @Nullable final ItemClickListener itemClickListener,
                      @Nullable final ItemLongClickListener itemLongClickListener)
    {
      super(itemView);
      itemView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (itemClickListener != null) itemClickListener.onItemClick(getView());
        }
      });
      itemView.setOnLongClickListener(new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
          if (itemLongClickListener != null) itemLongClickListener.onItemLongClick(getView());
          return true;
        }
      });
    }

    public ContactSelectionListItem getView() {
      return (ContactSelectionListItem) itemView;
    }
  }

  public static class HeaderViewHolder extends RecyclerView.ViewHolder {
    public HeaderViewHolder(View itemView) {
      super(itemView);
    }
  }

  public ContactSelectionListAdapter(@NonNull  Context context,
                                     @Nullable Cursor cursor,
                                     @Nullable ItemClickListener clickListener,
                                     @Nullable ItemLongClickListener longClickListener,
                                     boolean multiSelect)
  {
    super(context, cursor);
    this.li           = LayoutInflater.from(context);
    this.drawables    = context.obtainStyledAttributes(STYLE_ATTRIBUTES);
    this.multiSelect  = multiSelect;
    this.clickListener = clickListener;
    this.longClickListener = longClickListener;
  }

  @Override
  public long getHeaderId(int i) {
    return Util.hashCode(getHeaderString(i), isPush(i));
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    return new ViewHolder(li.inflate(R.layout.contact_selection_list_item, parent, false), clickListener, longClickListener);
  }

  @Override
  public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    long   id          = cursor.getLong(cursor.getColumnIndexOrThrow(Contacts.ID_COLUMN)); //ContactsDb.ID
    int    contactType = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.CONTACT_TYPE_COLUMN)); //Contacts.ATLAS_TYPE
    String name        = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.NAME_COLUMN)); //ContactsDb.NAME
    String number      = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.NUMBER_COLUMN)); //ContactsDb.ADDRESS
    String orgSlug       = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.LABEL_COLUMN)); //ContactsDb.ORGSLUG
    String slug = cursor.getString(cursor.getColumnIndex(Contacts.NUMBER_TYPE_COLUMN)); //ContactsDb.SLUG
    AtlasUser localUser = AtlasUser.getLocalUser(getContext());
    if (orgSlug == null) {
      slug = number;
    } else {
      if (!localUser.org_slug.equals(orgSlug)) {
        slug += ":" + orgSlug;
      }
    }

    int color = drawables.getColor(0, 0xa0000000);

    viewHolder.getView().unbind();
    viewHolder.getView().set(id, contactType, name, number, slug, color, multiSelect);
//    viewHolder.getView().setChecked(selectedContacts.containsKey(id));
    viewHolder.getView().setChecked(selectedAddresses.contains(number));
  }

  @Override
  public HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.contact_selection_recyclerview_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int position) {
    ((TextView)viewHolder.itemView).setText(getSpannedHeaderString(position));
  }

  @Override
  public CharSequence getBubbleText(int position) {
    return getHeaderString(position);
  }

  public Map<Long, String> getSelectedContacts() {
    return selectedContacts;
  }

  public Set<String> getSelectedAddresses() {
    return selectedAddresses;
  }

  private CharSequence getSpannedHeaderString(int position) {
    final String headerString = getHeaderString(position);
    if (isPush(position)) {
      SpannableString spannable = new SpannableString(headerString);
      spannable.setSpan(new ForegroundColorSpan(getContext().getResources().getColor(R.color.signal_primary)), 0, headerString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannable;
    } else {
      return headerString;
    }
  }

  private @NonNull String getHeaderString(int position) {
    Cursor cursor = getCursorAtPositionOrThrow(position);
    String letter = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.NAME_COLUMN));
    if (!TextUtils.isEmpty(letter)) {
      String firstChar = letter.trim().substring(0, 1).toUpperCase();
      if (Character.isLetterOrDigit(firstChar.codePointAt(0))) {
        return firstChar;
      }
    }

    return "#";
  }

  private boolean isPush(int position) {
    final Cursor cursor = getCursorAtPositionOrThrow(position);
    return cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.CONTACT_TYPE_COLUMN)) == Contacts.ATLAS_TYPE;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item);
  }

  public interface ItemLongClickListener {
    void onItemLongClick(ContactSelectionListItem item);
  }
}
