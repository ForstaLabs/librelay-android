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
package io.forsta.relay;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.preference.PreferenceFragment;

import io.forsta.librelay.LocalUserFragment;
import io.forsta.librelay.LogSubmitActivity;
import io.forsta.librelay.preferences.AdvancedPreferenceFragment;
import io.forsta.librelay.preferences.ChatsPreferenceFragment;
import io.forsta.librelay.preferences.NotificationsPreferenceFragment;
import io.forsta.librelay.util.DynamicLanguage;
import io.forsta.librelay.util.TextSecurePreferences;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends AuthenticationRequiredActionBarActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private static final String TAG = ApplicationPreferencesActivity.class.getSimpleName();

  private static final String PREFERENCE_CATEGORY_NOTIFICATIONS  = "preference_category_notifications";
  private static final String PREFERENCE_CATEGORY_CHATS          = "preference_category_chats";
  private static final String PREFERENCE_CATEGORY_ADVANCED       = "preference_category_advanced";
  private static final String PREFERENCE_CATEGORY_DEVICES        = "preference_category_devices";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();
  private AdvancedPreferenceFragment advancedPreferenceFragment;

  @Override
  protected void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.application_preference_activity);

    initFragment(R.id.forsta_local_user, new LocalUserFragment());


    if (icicle == null) {
      initFragment(R.id.forsta_preferences, new ApplicationPreferenceFragment());
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.forsta_preferences);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public boolean onSupportNavigateUp() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (fragmentManager.getBackStackEntryCount() > 0) {
      fragmentManager.popBackStack();
    } else {
      Intent intent = new Intent(this, ConversationListActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      finish();
    }
    return true;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(TextSecurePreferences.THEME_PREF)) {
      recreate();
    } else if (key.equals(TextSecurePreferences.LANGUAGE_PREF)) {
      recreate();
      dynamicLanguage.updateLocale(this);
    }
  }

  public static class ApplicationPreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);
      addPreferencesFromResource(R.xml.preferences);

      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_NOTIFICATIONS));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_CHATS));
      this.findPreference(PREFERENCE_CATEGORY_ADVANCED)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_ADVANCED));
      this.findPreference(PREFERENCE_CATEGORY_DEVICES)
          .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_DEVICES));
    }

    @Override
    public void onResume() {
      super.onResume();
      ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.text_secure_normal__menu_settings);
      setCategorySummaries();
    }

    private void setCategorySummaries() {
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
          .setSummary(NotificationsPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
          .setSummary(ChatsPreferenceFragment.getSummary(getActivity()));
    }

    private class CategoryClickListener implements Preference.OnPreferenceClickListener, AdvancedPreferenceFragment.OnClickCallbacks {
      private String       category;

      public CategoryClickListener(String category) {
        this.category     = category;
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Fragment fragment = null;

        switch (category) {
        case PREFERENCE_CATEGORY_NOTIFICATIONS:
          fragment = new NotificationsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_CHATS:
          fragment = new ChatsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_ADVANCED:
          fragment = new AdvancedPreferenceFragment();
          ((AdvancedPreferenceFragment) fragment).setOnClickCallbacks(this);
          break;
        case PREFERENCE_CATEGORY_DEVICES:
          Intent devicesIntent = new Intent(getActivity(), DeviceActivity.class);
          startActivity(devicesIntent);
          break;
        default:
          throw new AssertionError();
        }

        if (fragment != null) {
          Bundle args = new Bundle();
          fragment.setArguments(args);

          FragmentManager     fragmentManager     = getActivity().getSupportFragmentManager();
          FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
          fragmentTransaction.replace(R.id.forsta_preferences, fragment);
          fragmentTransaction.addToBackStack(null);
          fragmentTransaction.commit();
        }

        return true;
      }

      @Override
      public void onDebugLogPreferenceClick() {
        final Intent intent = new Intent(getContext(), LogSubmitActivity.class);
        startActivity(intent);
      }
    }
  }
}
