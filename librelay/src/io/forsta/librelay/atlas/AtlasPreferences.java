package io.forsta.librelay.atlas;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.forsta.librelay.atlas.model.AtlasJWT;
import io.forsta.librelay.util.TextSecurePreferences;

import java.util.Date;

/**
 * Created by jlewis on 1/6/17.
 */

public class AtlasPreferences {
  private static final String API_KEY = "api_key";
  private static final String API_LAST_LOGIN = "last_login";
  private static final String RELAY_LOGIN_PENDING = "relay_login_pending";
  private static final String RELAY_ORG_NAME = "relay_org_name";
  private static final String RELAY_ORG = "relay_org";
  private static final String RELAY_USER_NAME = "relay_user_name";
  private static final String RELAY_CONTACT_SYNC = "relay_contact_sync_time";
  private static final String RELAY_USER = "relay_user";
  private static final String RELAY_DEBUG = "relay_debug";

  public static void clearPreferences(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(API_KEY, "")
        .putString(API_LAST_LOGIN, "")
        .putBoolean(RELAY_LOGIN_PENDING, false)
        .putString(RELAY_ORG_NAME, "")
        .putString(RELAY_USER_NAME, "")
        .putString(RELAY_USER, "")
        .putBoolean(RELAY_DEBUG, false)
        .putLong(RELAY_CONTACT_SYNC, -1l)
        .apply();
  }

  public static void clearLogin(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(API_KEY, "")
        .putBoolean(RELAY_LOGIN_PENDING, false)
        .apply();
  }

  public static boolean isRegisteredAtlas(Context context) {
    return AtlasPreferences.getRegisteredKey(context) != "";
  }

  public static void setRegisteredAtlas(Context context, String value) {
    setStringPreference(context, API_KEY, value);
    AtlasJWT jwt = new AtlasJWT(value);
    TextSecurePreferences.setLocalAddress(context, jwt.getUid());
  }

  public static String getRegisteredKey(Context context) {
    return getStringPreference(context, API_KEY);
  }

  public static Date getTokenExpireDate(Context context) {
    String token = getStringPreference(context, API_KEY);
    AtlasJWT jwt = new AtlasJWT(token);
    return jwt.getExpireDate();
  }

  public static String getUserId(Context context) {
    String token = getStringPreference(context, API_KEY);
    AtlasJWT jwt = new AtlasJWT(token);
    return jwt.getUid();
  }

  public static void setCCSMDebug(Context context, boolean value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putBoolean(RELAY_DEBUG, value).apply();
  }

  public static boolean isCCSMDebug(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(RELAY_DEBUG, false);
  }

  public static void setForstaLoginPending(Context context, boolean pending) {
    setBooleanPreference(context, RELAY_LOGIN_PENDING, pending);
  }

  public static boolean getForstaLoginPending(Context context) {
    return getBooleanPreference(context, RELAY_LOGIN_PENDING);
  }

  public static void setForstaUsername(Context context, String userName) {
    setStringPreference(context, RELAY_USER_NAME, userName);
  }

  public static String getForstaUsername(Context context) {
    return getStringPreference(context, RELAY_USER_NAME);
  }

  public static void setForstaOrgName(Context context, String orgName) {
    setStringPreference(context, RELAY_ORG_NAME, orgName);
  }

  public static String getForstaOrgName(Context context) {
    return getStringPreference(context, RELAY_ORG_NAME);
  }

  public static void setForstaOrg(Context context, String json) {
    setStringPreference(context, RELAY_ORG, json);
  }

  public static String getForstaOrg(Context context) {
    return getStringPreference(context, RELAY_ORG);
  }

  public static long getForstaContactSync(Context context) {
    return getLongPreference(context, RELAY_CONTACT_SYNC);
  }

  public static void setForstaContactSync(Context context, long dateTime) {
    setLongPreference(context, RELAY_CONTACT_SYNC, dateTime);
  }

  public static String getForstaUser(Context context) {
    return getStringPreference(context, RELAY_USER);
  }

  public static void setForstaUser(Context context, String json) {
    setStringPreference(context, RELAY_USER, json);
  }

  private static void setStringPreference(Context context, String key, String value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(key, value).apply();
  }

  private static String getStringPreference(Context context, String key) {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(key, "");
  }

  private static void setBooleanPreference(Context context, String key, boolean value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putBoolean(key, value).apply();
  }

  private static boolean getBooleanPreference(Context context, String key) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, false);
  }

  private static void setLongPreference(Context context, String key, long value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putLong(key, value).apply();
  }

  private static long getLongPreference(Context context, String key) {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(key, -1l);
  }
}
