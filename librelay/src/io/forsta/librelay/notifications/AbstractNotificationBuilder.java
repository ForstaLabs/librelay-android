package io.forsta.librelay.notifications;

import android.app.Notification;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import io.forsta.librelay.R;
import io.forsta.librelay.database.RecipientPreferenceDatabase;
import io.forsta.librelay.preferences.NotificationPrivacyPreference;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

public abstract class AbstractNotificationBuilder extends NotificationCompat.Builder {

  protected Context                       context;
  protected NotificationPrivacyPreference privacy;

  public AbstractNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context);

    this.context = context;
    this.privacy = privacy;

    setColor(context.getResources().getColor(R.color.textsecure_primary));
    setSmallIcon(R.drawable.ic_message_white);
    setChannelId(NotificationChannels.getMessagesChannel(context));
    setLed();
  }

  protected void setNotificationChannel(String channel) {
    setChannelId(channel);
  }

  protected CharSequence getStyledMessage(@NonNull Recipient recipient, @Nullable CharSequence message) {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(Util.getBoldedString(recipient.toShortString()));
    builder.append(": ");
    builder.append(message == null ? "" : message);

    return builder;
  }

  public void setAlarms(@Nullable Uri ringtone, RecipientPreferenceDatabase.VibrateState vibrate) {
    Uri     defaultRingtone = NotificationChannels.supported() ? NotificationChannels.getMessageRingtone(context) : Uri.parse(TextSecurePreferences.getNotificationRingtone(context));
    boolean defaultVibrate = TextSecurePreferences.isNotificationVibrateEnabled(context);
    if (defaultVibrate && NotificationChannels.supported()) {
      defaultVibrate =  NotificationChannels.getMessageVibrate(context);
    }

    if      (ringtone == null && !TextUtils.isEmpty(defaultRingtone.toString())) setSound(defaultRingtone);
    else if (ringtone != null && !ringtone.toString().isEmpty())                 setSound(ringtone);

    if (vibrate == RecipientPreferenceDatabase.VibrateState.ENABLED ||
        (vibrate == RecipientPreferenceDatabase.VibrateState.DEFAULT && defaultVibrate))
    {
      setDefaults(Notification.DEFAULT_VIBRATE);
    }
  }

  private void setLed() {
    String ledColor              = TextSecurePreferences.getNotificationLedColor(context);
    String ledBlinkPattern       = TextSecurePreferences.getNotificationLedPattern(context);
    String ledBlinkPatternCustom = TextSecurePreferences.getNotificationLedPatternCustom(context);

    if (!ledColor.equals("none")) {
      String[] blinkPatternArray = parseBlinkPattern(ledBlinkPattern, ledBlinkPatternCustom);

      setLights(Color.parseColor(ledColor),
                Integer.parseInt(blinkPatternArray[0]),
                Integer.parseInt(blinkPatternArray[1]));
    }
  }

  public void setTicker(@NonNull Recipient recipient, @Nullable CharSequence message) {
    if (privacy.isDisplayMessage()) {
      setTicker(getStyledMessage(recipient, message));
    } else if (privacy.isDisplayContact()) {
      setTicker(getStyledMessage(recipient, context.getString(R.string.AbstractNotificationBuilder_new_message)));
    } else {
      setTicker(context.getString(R.string.AbstractNotificationBuilder_new_message));
    }
  }

  private String[] parseBlinkPattern(String blinkPattern, String blinkPatternCustom) {
    if (blinkPattern.equals("custom"))
      blinkPattern = blinkPatternCustom;

    return blinkPattern.split(",");
  }
}
