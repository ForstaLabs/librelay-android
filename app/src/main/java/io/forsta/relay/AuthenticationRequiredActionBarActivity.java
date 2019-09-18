package io.forsta.relay;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.util.Locale;

import io.forsta.relay.BaseActionBarActivity;
import io.forsta.relay.ConversationListActivity;
import io.forsta.relay.LoginActivity;
import io.forsta.relay.RegistrationActivity;

import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.util.TextSecurePreferences;

public abstract class AuthenticationRequiredActionBarActivity extends BaseActionBarActivity {
  private static final String TAG = AuthenticationRequiredActionBarActivity.class.getSimpleName();

  public static final String LOCALE_EXTRA = "locale_extra";

  private static final int STATE_NORMAL                   = 0;
  private static final int STATE_PROMPT_PUSH_REGISTRATION = 4;
  private static final int STATE_FORSTA_LOGIN             = 6;

  private boolean           isVisible;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.w(TAG, "onCreate(" + savedInstanceState + ")");
    onPreCreate();
    routeApplicationState();

    super.onCreate(savedInstanceState);
  }

  protected void onPreCreate() {}

  @Override
  protected void onResume() {
    Log.w(TAG, "onResume()");
    super.onResume();
    isVisible = true;
  }

  @Override
  protected void onPause() {
    Log.w(TAG, "onPause()");
    super.onPause();
    isVisible = false;
  }

  @Override
  protected void onDestroy() {
    Log.w(TAG, "onDestroy()");
    super.onDestroy();
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment)
  {
    return initFragment(target, fragment, null);
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @Nullable Locale locale)
  {
    return initFragment(target, fragment, locale, null);
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @Nullable Locale locale,
                                                @Nullable Bundle extras)
  {
    Bundle args = new Bundle();
    args.putSerializable(LOCALE_EXTRA, locale);

    if (extras != null) {
      args.putAll(extras);
    }

    fragment.setArguments(args);
    getSupportFragmentManager().beginTransaction()
                               .replace(target, fragment)
                               .commit();
    return fragment;
  }

  private void routeApplicationState() {
    Intent intent = getIntentForState(getApplicationState());
    if (intent != null) {
      startActivity(intent);
      finish();
    }
  }

  private Intent getIntentForState(int state) {
    Log.w(TAG, "routeApplicationState(), state: " + state);

    switch (state) {
      case STATE_PROMPT_PUSH_REGISTRATION: return getPushRegistrationIntent();
      case STATE_FORSTA_LOGIN:             return getAtlasLoginIntent();
      default:                             return null;
    }
  }

  private int getApplicationState() {
    if (!AtlasPreferences.isRegisteredAtlas(this)) {
      return STATE_FORSTA_LOGIN;
    } else if (!TextSecurePreferences.hasPromptedPushRegistration(this)) {
      return STATE_PROMPT_PUSH_REGISTRATION;
    } else {
      return STATE_NORMAL;
    }
  }

  private Intent getAtlasLoginIntent() {
    return getRoutedIntent(LoginActivity.class, getIntent());
  }

  private Intent getPushRegistrationIntent() {
    return getRoutedIntent(RegistrationActivity.class, new Intent(this, ConversationListActivity.class));
  }

  private Intent getRoutedIntent(Class<?> destination, @Nullable Intent nextIntent) {
    final Intent intent = new Intent(this, destination);
    if (nextIntent != null)   intent.putExtra("next_intent", nextIntent);
    return intent;
  }

}
