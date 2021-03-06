package io.forsta.librelay.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.database.ThreadPreferenceDatabase;
import io.forsta.librelay.recipients.Recipients;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import io.forsta.librelay.database.RecipientPreferenceDatabase;

public class NotificationState {
  private final LinkedList<NotificationItem> notifications = new LinkedList<>();
  private final LinkedHashSet<Long> threads = new LinkedHashSet<>();
  private boolean notify = false;
  private boolean vibrate = false;
  private String notificationChannel;

  private int notificationCount = 0;

  public NotificationState() {}

  @Override
  public String toString() {
    String separator = ", ";
    StringBuilder sb = new StringBuilder();
    sb.append("Notification #: ").append(notifications.size()).append(separator);
    sb.append("Count: ").append(notificationCount).append(separator);
    sb.append("Notify: ").append(notify).append(separator);
    sb.append("Vibrate: ").append(vibrate).append(separator);
    sb.append("Channel: ").append(notificationChannel).append(separator);

    return sb.toString();
  }

  public NotificationState(@NonNull List<NotificationItem> items) {
    for (NotificationItem item : items) {
      addNotification(item);
    }
  }

  public void addNotification(NotificationItem item) {
    notifications.addFirst(item);

    if (threads.contains(item.getThreadId())) {
      threads.remove(item.getThreadId());
    }

    threads.add(item.getThreadId());
    notificationCount++;
  }

  public @Nullable Uri getRingtone() {
    if (!notifications.isEmpty()) {
      ThreadPreferenceDatabase.ThreadPreference threadPreferences = notifications.getFirst().getThreadPreferences();
      if (threadPreferences != null) {
        return threadPreferences.getNotification();
      } else {
        return Settings.System.DEFAULT_NOTIFICATION_URI;
      }
    }

    return null;
  }

  public void setVibrateState(boolean vibrateState) {
    vibrate = vibrateState;
  }

  public boolean getVibrateState() {
    return vibrate;
  }

  public RecipientPreferenceDatabase.VibrateState getVibrate() {
    return RecipientPreferenceDatabase.VibrateState.DEFAULT;
  }

  public boolean hasMultipleThreads() {
    return threads.size() > 1;
  }

  public int getThreadCount() {
    return threads.size();
  }

  public int getNotificationCount() {
    return notificationCount;
  }

  public List<NotificationItem> getNotifications() {
    return notifications;
  }

  public PendingIntent getMarkAsReadIntent(Context context) {
    long[] threadArray = new long[threads.size()];
    int    index       = 0;

    for (long thread : threads) {
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.putExtra(MarkReadReceiver.THREAD_IDS_EXTRA, threadArray);
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getReplyIntent(Context context, long threadId) {
    if (threads.size() != 1) return null;

    Intent intent = new Intent(ReplyReceiver.REPLY_ACTION);
    intent.putExtra(ReplyReceiver.THREAD_ID, threadId);
    intent.setPackage(context.getPackageName());
    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public void setNotify(boolean state) {
    notify = state;
  }

  public boolean getNotify() {
    return notify;
  }

  public void setNotificationChannel(String channel) {
    this.notificationChannel = channel;
  }

  public String getNotificationChannel(Context context) {
    if (notificationChannel == null) {
      notificationChannel = NotificationChannels.getMessagesChannel(context);
      return notificationChannel;
    }
    return notificationChannel;
  }
}
