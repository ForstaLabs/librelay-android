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
package io.forsta.librelay.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.model.ThreadRecord;
import io.forsta.librelay.R;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase.MarkedMessageInfo;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.database.ThreadPreferenceDatabase;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.media.SlideDeck;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.ServiceUtil;
import io.forsta.librelay.util.SpanUtil;
import io.forsta.librelay.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import me.leolin.shortcutbadger.ShortcutBadger;


/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  private static final String TAG = MessageNotifier.class.getSimpleName();
  public static final int NOTIFICATION_ID = 1338;
  private static final String NOTIFICATION_GROUP = "messages";
  private volatile static long visibleThread = -1;
  private volatile static long lastUpdate = 0L;

  public static void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  public static void notifyMessageDeliveryFailed(Context context, long threadId) {
      FailedNotificationBuilder builder = new FailedNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify((int)threadId, builder.build());
  }



  // Main entry point for incoming message notifications.
  public static void updateNotification(@NonNull  Context context,
                                        long      threadId)
  {
    boolean isVisible = visibleThread == threadId;
    ThreadDatabase threads = DbFactory.getThreadDatabase(context);

    if (isVisible) {
      List<MarkedMessageInfo> messageIds = threads.setRead(threadId);
      MarkReadReceiver.process(context, messageIds);
    }

    if (!TextSecurePreferences.isNotificationsEnabled(context))
    {
      return;
    }

    updateNotification(context,true);
  }

  public static void updateNotification(@NonNull  Context context) {
    updateNotification(context,false);
  }

  private static void updateNotification(@NonNull  Context context, boolean signal)
  {
    if (!TextSecurePreferences.isNotificationsEnabled(context)) {
      return;
    }
    Cursor messageCursor = null;

    try {
      messageCursor = DbFactory.getMessageDatabase(context).getUnread();

      if ((messageCursor == null || messageCursor.isAfterLast()))
      {
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
        updateBadge(context, 0);
        return;
      }

      NotificationState notificationState = constructNotificationState(context, messageCursor);
      Log.w(TAG, "notificationState: " + notificationState);

      if (notificationState.hasMultipleThreads()) {
        sendMultipleThreadNotification(context, notificationState, signal);
      } else {
        sendSingleThreadNotification(context, notificationState, signal);
      }

      int unreadMessageCount = messageCursor.getCount();
      updateBadge(context, unreadMessageCount);

    } finally {
      if (messageCursor != null) messageCursor.close();
    }
  }

  private static void sendSingleThreadNotification(@NonNull  Context context,
                                                   @NonNull  NotificationState notificationState, boolean signal)
  {


    if (notificationState.getNotifications().isEmpty()) {
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
      return;
    }

    SingleRecipientNotificationBuilder builder       = new SingleRecipientNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
    builder.setNotificationChannel(notificationState.getNotificationChannel(context));
    List<NotificationItem>             notifications = notificationState.getNotifications();
    Recipients                         recipients    = notifications.get(0).getRecipients();

    builder.setThread(notifications.get(0).getRecipients(), notifications.get(0).getTitle().toString());
    builder.setMessageCount(notificationState.getMessageCount());
    builder.setPrimaryMessageBody(recipients, notifications.get(0).getIndividualRecipient(),
                                  notifications.get(0).getText(), notifications.get(0).getSlideDeck());
    builder.setGroup(NOTIFICATION_GROUP);
    builder.setOnlyAlertOnce(!notificationState.getVibrateState());

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(notificationState.getMarkAsReadIntent(context), null);
                       notificationState.getQuickReplyIntent(context, notifications.get(0).getRecipients());

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getRecipients(), item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(NOTIFICATION_ID, builder.build());

    lastUpdate = System.currentTimeMillis();
  }

  private static void sendMultipleThreadNotification(@NonNull  Context context,
                                                     @NonNull  NotificationState notificationState,
                                                     boolean signal)
  {
    MultipleRecipientNotificationBuilder builder       = new MultipleRecipientNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
    builder.setNotificationChannel(notificationState.getNotificationChannel(context));
    List<NotificationItem>               notifications = notificationState.getNotifications();

    builder.setMessageCount(notificationState.getMessageCount(), notificationState.getThreadCount());
    builder.setMostRecentSender(notifications.get(0).getIndividualRecipient());
    builder.setGroup(NOTIFICATION_GROUP);
    Log.w(TAG, "Vibrate " + notificationState.getVibrateState());
    builder.setOnlyAlertOnce(!notificationState.getVibrateState());
    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(notificationState.getMarkAsReadIntent(context));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getIndividualRecipient(), item.getText());
    }

    if (notificationState.getVibrateState()) {
      builder.setAlarms(notificationState.getRingtone(), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(NOTIFICATION_ID, builder.build());

    lastUpdate = System.currentTimeMillis();
  }

  private static NotificationState constructNotificationState(@NonNull  Context context,
                                                              @NonNull  Cursor cursor)
  {
    NotificationState notificationState = new NotificationState();
    MessageRecord record;
    MessageDatabase.Reader reader = DbFactory.getMessageDatabase(context).readerFor(cursor);
    Set<String> filters = TextSecurePreferences.getNotificationPreferences(context);
    boolean previousVibrate = false;
    List<Long> notifiedThreads = new ArrayList<>();
    Set<Long> notificationThreads = new HashSet<>();

    while ((record = reader.getNext()) != null) {
      long threadId = record.getThreadId();
      Recipients threadRecipients = DbFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);
      Recipient sender = record.getIndividualRecipient();
      if (threadRecipients == null || threadRecipients.isEmpty()) {
        Log.w(TAG, "Thread has no recipients. Setting to sender");
          threadRecipients = record.getRecipients();
      }
      ThreadRecord forstaThread = DbFactory.getThreadDatabase(context).getThread(threadId);
      ThreadPreferenceDatabase.ThreadPreference threadPreferences = DbFactory.getThreadPreferenceDatabase(context).getThreadPreferences(threadId);

      CharSequence title = forstaThread != null ? forstaThread.getTitle() : "";
      CharSequence body = record.getPlainTextBody();
      SlideDeck slideDeck = null;
      long timestamp = record.getTimestamp();

      boolean isDirectMessage = threadRecipients != null && threadRecipients.isSingleRecipient();
      boolean isNamed = record.isNamed(context);
      boolean isMentioned = record.isMentioned(context);
      boolean threadNotification = showThreadNotification(context, threadId);
      boolean messageNotification = showFilteredNotification(filters, isDirectMessage, isNamed, isMentioned);

      if (record.isMediaPending() && TextUtils.isEmpty(body)) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_media_message));
        slideDeck = record.getSlideDeck();
      } else if (record.isMediaPending()) {
        String message = context.getString(R.string.MessageNotifier_media_message_with_text, body);
        int italicLength = message.length() - body.length();
        body = SpanUtil.italic(message, italicLength);
        slideDeck = record.getSlideDeck();
      }

      if (threadNotification && messageNotification) {
        notificationState.addNotification(new NotificationItem(sender, threadPreferences, threadRecipients, threadId, body, title, timestamp, slideDeck));
        notificationState.setNotify(true);

        if (!previousVibrate || !notificationThreads.contains(threadId)) {
          previousVibrate = true;
          notificationState.setVibrateState(true);
        } else {
          notificationState.setVibrateState(false);
        }

        notificationThreads.add(threadId);
      } else {
        notificationState.setVibrateState(false);
      }
    }

    if (notificationState.getVibrateState()) {
      notificationState.setNotificationChannel(NotificationChannels.getMessagesChannel(context));
    } else if (notificationState.getNotify()) {
      notificationState.setNotificationChannel(NotificationChannels.MESSAGES_LOW);
    } else {
      // Update badge only
      notificationState.setNotificationChannel(NotificationChannels.MESSAGES_MIN);
    }

    if (notificationState.getNotifications().size() < 1) {
      notificationState.setNotify(false);
      notificationState.setVibrateState(false);
    }

    reader.close();
    return notificationState;
  }

  private static void updateBadge(Context context, int count) {
    try {
      if (count == 0) ShortcutBadger.removeCount(context);
      else            ShortcutBadger.applyCount(context, count);
    } catch (Throwable t) {
      // NOTE :: I don't totally trust this thing, so I'm catching
      // everything.
      Log.w("MessageNotifier", t);
    }
  }

  public static class DeleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

    }
  }

  private static boolean showThreadNotification(Context context, long threadId) {
    ThreadPreferenceDatabase.ThreadPreference threadPreference = DbFactory.getThreadPreferenceDatabase(context).getThreadPreferences(threadId);

    if (threadPreference != null && threadPreference.isMuted()) {
      return false;
    }
    return true;
  }

  private static boolean showFilteredNotification(Set<String> filters, boolean isDirect, boolean isNamed, boolean isMentioned) {
    boolean result = true;
    if (filters.size() > 0) {
      result = false;
      for (String item : filters) {
        if (item.equals("dm")) {
          if (isDirect) {
            result = true;
          }
        }

        if (item.equals("mention")) {
          if (isMentioned) {
            result = true;
          }
        }

        if (item.equals("name")) {
          if (isNamed) {
            result = true;
          }
        }
      }
    }

    return result;
  }
}
