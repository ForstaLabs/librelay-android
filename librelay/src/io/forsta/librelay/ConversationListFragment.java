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

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.ConversationListAdapter.ItemClickListener;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase.MarkedMessageInfo;
import io.forsta.librelay.database.loaders.ConversationListLoader;
import io.forsta.librelay.notifications.MarkReadReceiver;
import io.forsta.librelay.notifications.MessageNotifier;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.ViewUtil;
import io.forsta.librelay.util.task.SnackbarAsyncTask;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class ConversationListFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback, ItemClickListener
{
  public static final String LOCALE_EXTRA = "locale_extra";
  public static final String ARCHIVE = "archive";

  private ActionMode           actionMode;
  private RecyclerView         list;
  private FloatingActionButton fab;
  private Locale               locale;
  private String               queryFilter  = "";
  private boolean              archive;
  private NewConversationClickListener newConversationClickListener;



  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    locale       = (Locale) getArguments().getSerializable(LOCALE_EXTRA);
    archive      = getArguments().getBoolean(ARCHIVE, false);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_list_fragment, container, false);

    list         = ViewUtil.findById(view, R.id.list);
    fab          = ViewUtil.findById(view, R.id.fab);

    if (archive) fab.setVisibility(View.GONE);
    else         fab.setVisibility(View.VISIBLE);

    list.setHasFixedSize(true);
    LinearLayoutManager lm = new LinearLayoutManager(getActivity());
    list.setLayoutManager(lm);

    new ItemTouchHelper(new ArchiveListenerCallback()).attachToRecyclerView(list);

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    fab.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (newConversationClickListener != null) {
          newConversationClickListener.onClick();
        }
      }
    });
    initializeListAdapter();
  }

  @Override
  public void onResume() {
    super.onResume();
    list.getAdapter().notifyDataSetChanged();
  }

  public void setNewConversationClickListener(NewConversationClickListener listener) {
    this.newConversationClickListener = listener;
  }

  public ConversationListAdapter getListAdapter() {
    return (ConversationListAdapter) list.getAdapter();
  }

  public void setQueryFilter(String query) {
    this.queryFilter = query;
    getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    if (!TextUtils.isEmpty(this.queryFilter)) {
      setQueryFilter("");
    }
  }

  private void initializeListAdapter() {
    list.setAdapter(new ConversationListAdapter(getActivity(), locale, null, this));
    getLoaderManager().restartLoader(0, null, this);
  }

  private void handleArchiveAllSelected() {
    final Set<Long> selectedConversations = new HashSet<>(getListAdapter().getBatchSelections());
    final boolean   archive               = this.archive;

    int snackBarTitleId;

    if (archive) snackBarTitleId = R.plurals.ConversationListFragment_moved_conversations_to_inbox;
    else         snackBarTitleId = R.plurals.ConversationListFragment_conversations_archived;

    int count            = selectedConversations.size();
    String snackBarTitle = getResources().getQuantityString(snackBarTitleId, count, count);

    new SnackbarAsyncTask<Void>(getView(), snackBarTitle,
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG, true)
    {

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);

        if (actionMode != null) {
          actionMode.finish();
          actionMode = null;
        }
      }

      @Override
      protected void executeAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          if (!archive) DbFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
          else          DbFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
        }
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          if (!archive) DbFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
          else          DbFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
        }
      }
    }.execute();
  }

  private void handleDeleteAllSelected() {
    int                 conversationsCount = getListAdapter().getBatchSelections().size();
    AlertDialog.Builder alert              = new AlertDialog.Builder(getActivity());
    alert.setIconAttribute(R.attr.dialog_alert_icon);
    alert.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                                  conversationsCount, conversationsCount));
    alert.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                                    conversationsCount, conversationsCount));
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        final Set<Long> selectedConversations = (getListAdapter())
            .getBatchSelections();

        if (!selectedConversations.isEmpty()) {
          new AsyncTask<Void, Void, Void>() {
            private ProgressDialog dialog;

            @Override
            protected void onPreExecute() {
              dialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ConversationListFragment_deleting),
                                           getActivity().getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                           true, false);
            }

            @Override
            protected Void doInBackground(Void... params) {
              DbFactory.getThreadDatabase(getActivity()).deleteConversations(selectedConversations);
              MessageNotifier.updateNotification(getActivity());
              return null;
            }

            @Override
            protected void onPostExecute(Void result) {
              dialog.dismiss();
              if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
              }
            }
          }.execute();
        }
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handleSelectAllThreads() {
    getListAdapter().selectAllThreads();
    actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount, String.valueOf(getListAdapter().getBatchSelections().size())));
  }

  private void handlePinAllSelected() {
    final Set<Long> selectedConversations = (getListAdapter())
        .getBatchSelections();

    new AsyncTask<Void, Void, Void>() {

      @Override
      protected Void doInBackground(Void... voids) {
        for (Long threadId : selectedConversations) {
          ThreadRecord thread = DbFactory.getThreadDatabase(getActivity()).getThread(threadId);
          DbFactory.getThreadDatabase(getActivity()).updatePinned(threadId, !thread.isPinned());
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        if (actionMode != null) {
          actionMode.finish();
          actionMode = null;
        }
      }
    }.execute();
  }

  private void handleCreateConversation(long threadId, Recipients recipients, int distributionType) {
    ((ConversationSelectedListener)getActivity()).onCreateConversation(threadId, recipients, distributionType);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationListLoader(getActivity(), queryFilter, archive, true);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    getListAdapter().changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    getListAdapter().changeCursor(null);
  }

  @Override
  public void onItemClick(ConversationListItem item) {
    if (actionMode == null) {
      handleCreateConversation(item.getThreadId(), item.getRecipients(),
                               item.getDistributionType());
    } else {
      ConversationListAdapter adapter = (ConversationListAdapter)list.getAdapter();
      adapter.toggleThreadInBatchSet(item.getThreadId());

      if (adapter.getBatchSelections().size() == 0) {
        actionMode.finish();
      } else {
        actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                                         String.valueOf(adapter.getBatchSelections().size())));
      }

      adapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onItemLongClick(ConversationListItem item) {
    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(ConversationListFragment.this);

    getListAdapter().initializeBatchMode(true);
    getListAdapter().toggleThreadInBatchSet(item.getThreadId());
    getListAdapter().notifyDataSetChanged();
  }

  @Override
  public void onSwitchToArchive() {
    ((ConversationSelectedListener)getActivity()).onSwitchToArchive();
  }

  public interface ConversationSelectedListener {
    void onCreateConversation(long threadId, Recipients recipients, int distributionType);
    void onSwitchToArchive();
}

  public interface NewConversationClickListener {
    void onClick();
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getActivity().getMenuInflater();

    if (archive) inflater.inflate(R.menu.conversation_list_batch_unarchive, menu);
    else         inflater.inflate(R.menu.conversation_list_batch_archive, menu);

    inflater.inflate(R.menu.conversation_list_batch, menu);

//    mode.setTitle(R.string.conversation_fragment_cab__batch_selection_mode);
    mode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount, String.valueOf(1)));

    getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));

    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    int i = item.getItemId();
    if (i == R.id.menu_select_all) {
      handleSelectAllThreads();
      return true;
    } else if (i == R.id.menu_delete_selected) {
      handleDeleteAllSelected();
      return true;
    } else if (i == R.id.menu_archive_selected) {
      handleArchiveAllSelected();
      return true;
    } else if (i == R.id.menu_pin_selected) {
      handlePinAllSelected();
      return true;
    }

    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    getListAdapter().initializeBatchMode(false);
    TypedArray color = getActivity().getTheme().obtainStyledAttributes(new int[] {android.R.attr.statusBarColor});
    getActivity().getWindow().setStatusBarColor(color.getColor(0, Color.BLACK));
    color.recycle();

    actionMode = null;
  }

  private class ArchiveListenerCallback extends ItemTouchHelper.SimpleCallback {

    public ArchiveListenerCallback() {
      super(0, ItemTouchHelper.RIGHT);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView,
                          RecyclerView.ViewHolder viewHolder,
                          RecyclerView.ViewHolder target)
    {
      return false;
    }

    @Override
    public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
      if (viewHolder.itemView instanceof ConversationListItemAction) {
        return 0;
      }

      if (actionMode != null) {
        return 0;
      }

      return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
      final long    threadId = ((ConversationListItem)viewHolder.itemView).getThreadId();
      final boolean read     = ((ConversationListItem)viewHolder.itemView).getRead();

      if (archive) {
        new SnackbarAsyncTask<Long>(getView(),
                                    getResources().getQuantityString(R.plurals.ConversationListFragment_moved_conversations_to_inbox, 1, 1),
                                    getString(R.string.ConversationListFragment_undo),
                                    getResources().getColor(R.color.amber_500),
                                    Snackbar.LENGTH_LONG, false)
        {
          @Override
          protected void executeAction(@Nullable Long parameter) {
            DbFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
          }

          @Override
          protected void reverseAction(@Nullable Long parameter) {
            DbFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
          }
        }.execute(threadId);
      } else {
        new SnackbarAsyncTask<Long>(getView(),
                                    getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
                                    getString(R.string.ConversationListFragment_undo),
                                    getResources().getColor(R.color.amber_500),
                                    Snackbar.LENGTH_LONG, false)
        {
          @Override
          protected void executeAction(@Nullable Long parameter) {
            DbFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);

            if (!read) {
              List<MarkedMessageInfo> messageIds = DbFactory.getThreadDatabase(getActivity()).setRead(threadId);
              MessageNotifier.updateNotification(getActivity());
              MarkReadReceiver.process(getActivity(), messageIds);
            }
          }

          @Override
          protected void reverseAction(@Nullable Long parameter) {
            DbFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);

            if (!read) {
              DbFactory.getThreadDatabase(getActivity()).setUnread(threadId);
              MessageNotifier.updateNotification(getActivity());
            }
          }
        }.execute(threadId);
      }
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView,
                            RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState,
                            boolean isCurrentlyActive)
    {

      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        View  itemView = viewHolder.itemView;
        Paint p        = new Paint();

        if (dX > 0) {
          Bitmap icon;

          if (archive) icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_unarchive_white_36dp);
          else         icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_archive_white_36dp);

          p.setColor(getResources().getColor(R.color.green_500));

          c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX,
                     (float) itemView.getBottom(), p);

          c.drawBitmap(icon,
                       (float) itemView.getLeft() + getResources().getDimension(R.dimen.conversation_list_fragment_archive_padding),
                       (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getHeight())/2,
                       p);
        }

        float alpha = 1.0f - Math.abs(dX) / (float) viewHolder.itemView.getWidth();
        viewHolder.itemView.setAlpha(alpha);
        viewHolder.itemView.setTranslationX(dX);
      } else {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
      }
    }
  }

}


