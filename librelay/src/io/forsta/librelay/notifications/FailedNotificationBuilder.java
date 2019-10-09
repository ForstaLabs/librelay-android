package io.forsta.librelay.notifications;

import android.content.Context;
import android.graphics.BitmapFactory;

import io.forsta.librelay.R;
import io.forsta.librelay.database.RecipientPreferenceDatabase;
import io.forsta.librelay.preferences.NotificationPrivacyPreference;

public class FailedNotificationBuilder extends AbstractNotificationBuilder {

  public FailedNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    setSmallIcon(R.drawable.ic_notifications_gray);
    setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                              R.drawable.ic_action_warning_red));
    setContentTitle(context.getString(R.string.MessageNotifier_message_delivery_failed));
    setContentText(context.getString(R.string.MessageNotifier_failed_to_deliver_message));
    setTicker(context.getString(R.string.MessageNotifier_error_delivering_message));
    setAutoCancel(true);
    setAlarms(null, RecipientPreferenceDatabase.VibrateState.DEFAULT);
    setChannelId(NotificationChannels.FAILURES);
  }
}
