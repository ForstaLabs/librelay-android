package io.forsta.librelay.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import io.forsta.librelay.ApplicationContext;
import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase.ExpirationInfo;
import io.forsta.librelay.database.MessageDatabase.MarkedMessageInfo;
import io.forsta.librelay.database.MessageDatabase.SyncMessageId;
import io.forsta.librelay.jobs.MultiDeviceReadUpdateJob;
import io.forsta.librelay.service.ExpiringMessageManager;

import java.util.LinkedList;
import java.util.List;

public class MarkReadReceiver extends BroadcastReceiver {

  private static final String TAG              = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION     = BuildConfig.APPLICATION_ID + "." + BuildConfig.FLAVOR + ".notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA = "thread_ids";

  @Override
  public void onReceive(final Context context, Intent intent)
  {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      Log.w("TAG", "threadIds length: " + threadIds.length);

      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
                                   .cancel(MessageNotifier.NOTIFICATION_ID);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();

          for (long threadId : threadIds) {
            Log.w(TAG, "Marking as read: " + threadId);
            List<MarkedMessageInfo> messageIds = DbFactory.getThreadDatabase(context).setRead(threadId);
            messageIdsCollection.addAll(messageIds);
          }

          process(context, messageIdsCollection);

          MessageNotifier.updateNotification(context);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  public static void process(@NonNull Context context, @NonNull List<MarkedMessageInfo> markedReadMessages) {
    if (markedReadMessages.isEmpty()) return;

    List<SyncMessageId> syncMessageIds = new LinkedList<>();

    for (MarkedMessageInfo messageInfo : markedReadMessages) {
      scheduleDeletion(context, messageInfo.getExpirationInfo());
      syncMessageIds.add(messageInfo.getSyncMessageId());
    }

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new MultiDeviceReadUpdateJob(context, syncMessageIds));
  }

  private static void scheduleDeletion(Context context, ExpirationInfo expirationInfo) {
    if (expirationInfo.getExpiresIn() > 0 && expirationInfo.getExpireStarted() <= 0) {
      ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      DbFactory.getMessageDatabase(context).markExpireStarted(expirationInfo.getId());

      expirationManager.scheduleDeletion(expirationInfo.getId(), expirationInfo.getExpiresIn());
    }
  }
}
