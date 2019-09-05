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
package io.forsta.librelay;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import io.forsta.librelay.ImageMediaAdapter.ViewHolder;
import io.forsta.librelay.components.ThumbnailView;
import io.forsta.librelay.database.CursorRecyclerViewAdapter;
import io.forsta.librelay.database.ImageDatabase.ImageRecord;
import io.forsta.librelay.media.Slide;
import io.forsta.librelay.util.MediaUtil;

public class ImageMediaAdapter extends CursorRecyclerViewAdapter<ViewHolder> {
  private static final String TAG = ImageMediaAdapter.class.getSimpleName();

  private MediaClickListener mediaClickListener;

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ThumbnailView imageView;

    public ViewHolder(View v) {
      super(v);
      imageView = (ThumbnailView) v.findViewById(R.id.image);
    }
  }

  public ImageMediaAdapter(Context context, Cursor c) {
    super(context, c);
  }

  public ImageMediaAdapter(Context context, Cursor c, MediaClickListener listener) {
    super(context, c);
    this.mediaClickListener = listener;
  }

  @Override
  public ViewHolder onCreateItemViewHolder(final ViewGroup viewGroup, final int i) {
    final View view = LayoutInflater.from(getContext()).inflate(R.layout.media_overview_item, viewGroup, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindItemViewHolder(final ViewHolder viewHolder, final @NonNull Cursor cursor) {
    final ThumbnailView imageView   = viewHolder.imageView;
    final ImageRecord imageRecord = ImageRecord.from(cursor);

    Slide slide = MediaUtil.getSlideForAttachment(getContext(), imageRecord.getAttachment());

    if (slide != null) {
      imageView.setImageResource(slide, false);
    }

    imageView.setOnClickListener(new OnMediaClickListener(imageRecord));
  }

  public interface MediaClickListener {
    void onClick(ImageRecord imageRecord);
  }

  private class OnMediaClickListener implements OnClickListener {
    private final ImageRecord imageRecord;

    private OnMediaClickListener(ImageRecord imageRecord) {
      this.imageRecord = imageRecord;
    }

    @Override
    public void onClick(View v) {
      if (mediaClickListener != null) {
        mediaClickListener.onClick(imageRecord);
      }
    }
  }
}
