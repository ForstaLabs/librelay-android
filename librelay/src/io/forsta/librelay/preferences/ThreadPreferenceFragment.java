package io.forsta.librelay.preferences;

import android.graphics.drawable.ColorDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.support.v4.preference.PreferenceFragment;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

import io.forsta.librelay.color.MaterialColor;
import io.forsta.librelay.color.MaterialColors;
import io.forsta.librelay.components.MuteDialog;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.ThreadDatabase;
import io.forsta.librelay.database.ThreadPreferenceDatabase;
import io.forsta.librelay.database.model.ThreadRecord;

public class ThreadPreferenceFragment extends PreferenceFragment {
  public static final String THREAD_ID_EXTRA = "thread_id";

  private static final String PREFERENCE_PINNED    = "pref_key_thread_pinned";
  private static final String PREFERENCE_MUTED    = "pref_key_thread_mute";
  private static final String PREFERENCE_TONE     = "pref_key_thread_ringtone";
  private static final String PREFERENCE_VIBRATE  = "pref_key_thread_vibrate";
  private static final String PREFERENCE_BLOCK    = "pref_key_thread_block";
  private static final String PREFERENCE_COLOR    = "pref_key_thread_color";
  private static final String PREFERENCE_IDENTITY = "pref_key_thread_identity";


  private long threadId;
  private CheckBoxPreference pinnedPreference;
  private CheckBoxPreference mutePreference;
  private AdvancedRingtonePreference notificationPreference;
  private ListPreference vibratePreference;
  private ColorPreference colorPreference;
  private Preference blockPreference;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.threadId = getArguments().getLong(THREAD_ID_EXTRA);

    addPreferencesFromResource(io.forsta.librelay.R.xml.thread_preferences);
    initializePreferences();

    this.findPreference(PREFERENCE_PINNED).setOnPreferenceChangeListener(new PinnedChangedListener());
    this.findPreference(PREFERENCE_MUTED)
        .setOnPreferenceClickListener(new MuteClickListener());
    this.findPreference(PREFERENCE_COLOR)
        .setOnPreferenceChangeListener(new ColorChangeListener());
  }

  private void initializePreferences() {
    ThreadPreferenceDatabase.ThreadPreference threadPreference = DbFactory.getThreadPreferenceDatabase(getActivity()).getThreadPreferences(threadId);
    ThreadDatabase threadDatabase = DbFactory.getThreadDatabase(getActivity());
    ThreadRecord forstaThead = threadDatabase.getThread(threadId);

    pinnedPreference = (CheckBoxPreference) this.findPreference(PREFERENCE_PINNED);
    mutePreference = (CheckBoxPreference) this.findPreference(PREFERENCE_MUTED);
    colorPreference = (ColorPreference) this.findPreference(PREFERENCE_COLOR);

    colorPreference.setChoices(MaterialColors.CONVERSATION_PALETTE.asConversationColorArray(getActivity()));
    colorPreference.setValue(threadPreference.getColor().toConversationColor(getActivity()));
    pinnedPreference.setChecked(forstaThead.isPinned());
    mutePreference.setChecked(threadPreference.isMuted());
    setMuteSummary();
  }

  private void setMuteSummary() {
    ThreadPreferenceDatabase.ThreadPreference threadPreference = DbFactory.getThreadPreferenceDatabase(getActivity()).getThreadPreferences(threadId);
    long muteSetting = threadPreference.getMuteUntil();
    if (muteSetting != 0) {
      if (muteSetting == -1) {
        mutePreference.setSummary("Mute permanently");
      } else {
        SimpleDateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm");
        Date muteUntil = new Date(muteSetting);
        mutePreference.setSummary("Mute until " + df.format(muteUntil));
      }
    } else {
      mutePreference.setSummary(io.forsta.librelay.R.string.thread_preferences__disable_notifications_for_this_conversation);
    }
  }

  private class RingtoneChangeListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      String value = (String)newValue;

      final Uri uri;

      if (TextUtils.isEmpty(value) || Settings.System.DEFAULT_NOTIFICATION_URI.toString().equals(value)) {
        uri = null;
      } else {
        uri = Uri.parse(value);
      }

      new AsyncTask<Uri, Void, Void>() {
        @Override
        protected Void doInBackground(Uri... params) {
          DbFactory.getThreadPreferenceDatabase(getActivity()).setNotification(threadId, params[0]);
          return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
          notificationPreference.setCurrentRingtone(uri);
          if (uri == null) {
            notificationPreference.setSummary(io.forsta.librelay.R.string.preferences__default);
          } else {
            Ringtone tone = RingtoneManager.getRingtone(getActivity(), uri);
            if (tone != null) {
              notificationPreference.setSummary(tone.getTitle(getActivity()));
            }
          }
        }
      }.execute(uri);

      return false;
    }
  }

  private class VibrateChangeListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      return false;
    }
  }

  private class PinnedChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      final boolean pinned = (boolean) newValue;
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DbFactory.getThreadDatabase(getActivity()).updatePinned(threadId, pinned);
          return null;
        }
      }.execute();
      return true;
    }
  }

  private class MuteClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {

      ThreadPreferenceDatabase.ThreadPreference threadPreference = DbFactory.getThreadPreferenceDatabase(getActivity()).getThreadPreferences(threadId);
      if (threadPreference != null && threadPreference.isMuted()) {
        new AsyncTask<Long, Void, Void>() {

          @Override
          protected Void doInBackground(Long... params) {
            DbFactory.getThreadPreferenceDatabase(getActivity()).setMuteUntil(threadId, params[0]);
            return null;
          }

          @Override
          protected void onPostExecute(Void aVoid) {
            setMuteSummary();
          }
        }.execute(0L);
      } else {
        MuteDialog.show(getActivity(), new MuteDialog.MuteSelectionListener() {
          @Override
          public void onMuted(long until) {
            new AsyncTask<Long, Void, Void>() {

              @Override
              protected Void doInBackground(Long... params) {
                DbFactory.getThreadPreferenceDatabase(getActivity()).setMuteUntil(threadId, params[0]);
                return null;
              }

              @Override
              protected void onPostExecute(Void aVoid) {
                setMuteSummary();
              }
            }.execute(until);
          }
        });
      }
      return false;
    }
  }

  private class BlockChangeListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      return false;
    }
  }

  private class ColorChangeListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      final int           value         = (Integer) o;
      final MaterialColor selectedColor = MaterialColors.CONVERSATION_PALETTE.getByColor(getActivity(), value);
      DbFactory.getThreadPreferenceDatabase(getActivity()).setColor(threadId, selectedColor);
      return true;
    }
  }
}