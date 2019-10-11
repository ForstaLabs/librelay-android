package io.forsta.librelay.preferences;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import io.forsta.librelay.R;

public class AdvancedPreferenceFragment extends PreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";
  private OnClickCallbacks onClickCallbacks;

  public interface OnClickCallbacks {
    void onDebugLogPreferenceClick();
  }

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_advanced);

    Preference submitDebugLog = this.findPreference(SUBMIT_DEBUG_LOG_PREF);
    submitDebugLog.setOnPreferenceClickListener(preference -> {
      if (onClickCallbacks != null) {
        onClickCallbacks.onDebugLogPreferenceClick();
        return true;
      }
      return false;
    });
    submitDebugLog.setSummary(getVersion(getActivity()));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);
  }

  public void setOnClickCallbacks(OnClickCallbacks callbacks) {
    this.onClickCallbacks = callbacks;
  }

  private @NonNull String getVersion(@Nullable Context context) {
    try {
      if (context == null) return "";

      String app     = context.getString(R.string.app_name);
      String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;

      return String.format("%s %s", app, version);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
      return context.getString(R.string.app_name);
    }
  }
}
