// vim: ts=2:sw=2:expandtab
package io.forsta.librelay.atlas;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import io.forsta.librelay.atlas.model.RelayDistribution;
import io.forsta.librelay.database.Contacts;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.atlas.model.AtlasOrg;
import io.forsta.librelay.atlas.model.AtlasTag;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.BuildConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.librelay.database.GroupDatabase;
import io.forsta.librelay.permissions.Permissions;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;
import io.forsta.librelay.util.NetworkUtils;

/**
 * Created by jlewis on 1/18/17.
 */

public class AtlasApi {
  private static final String TAG = AtlasApi.class.getSimpleName();

  private static final String API_TOKEN_REFRESH = "/v1/api-token-refresh/";
  private static final String API_LOGIN = "/v1/login/";
  private static final String API_JOIN = "/v1/join/";
  private static final String API_USER = "/v1/user/";
  private static final String API_USER_PICK = "/v1/user-pick/";
  private static final String API_TAG = "/v1/tag/";
  private static final String API_TAG_PICK = "/v1/tag-pick/";
  private static final String API_USER_TAG = "/v1/usertag/";
  private static final String API_ORG = "/v1/org/";
  private static final String API_DIRECTORY_USER = "/v1/directory/user/";
  private static final String API_DIRECTORY_DOMAIN = "/v1/directory/org/";
  private static final String API_SEND_TOKEN = "/v1/login/send/";
  private static final String API_PROVISION_ACCOUNT = "/v1/provision/account/";
  private static final String API_PROVISION_REQUEST = "/v1/provision/request/";
  private static final String API_USER_RESET_PASSWORD = "/v1/password/reset/";
  private static final String API_RTC = "/v1/rtc/servers/";
  private static final String API_TAGMATH = "/v1/tagmath";
  private static final long EXPIRE_REFRESH_DELTA = 7L;

  private AtlasApi() {
  }

  public static boolean hasDevices(Context context) {
    String host = BuildConfig.FORSTA_API_URL;
    String authKey = AtlasPreferences.getRegisteredKey(context);
    JSONObject response = NetworkUtils.apiFetch("GET", authKey, host + API_PROVISION_ACCOUNT, null);
    if (response.has("devices")) {
      try {
        JSONArray devices = response.getJSONArray("devices");
        if (devices.length() > 0) {
          return true;
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  public static void provisionRequest(Context context, String uuid, String pubKey) {
    try {
      JSONObject obj = new JSONObject();
      obj.put("uuid", uuid);
      obj.put("key", pubKey);
      fetchResource(context, "POST", API_PROVISION_REQUEST, obj);
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public static JSONObject provisionAccount(Context context, JSONObject obj) throws Exception {
    return hardFetchResource(context, "PUT", API_PROVISION_ACCOUNT, obj);
  }

  public static JSONObject accountJoin(JSONObject jsonObject) {
    return NetworkUtils.apiFetch("POST", null, BuildConfig.FORSTA_API_URL + "" + API_JOIN, jsonObject);
  }

  public static JSONObject atlasLogin(Context context, JSONObject authObject) {
    String host = BuildConfig.FORSTA_API_URL;
    JSONObject result = NetworkUtils.apiFetch("POST", null, host + API_LOGIN, authObject);
    return result;
  }

  public static JSONObject atlasSendToken(Context context, String org, String username) {
    String host = BuildConfig.FORSTA_API_URL;
    JSONObject result = NetworkUtils.apiFetch("GET", null, host + API_SEND_TOKEN + org + "/" + username + "/", null);
    return result;
  }

  public static boolean tokenNeedsRefresh(Context context) {
    Date expireDate = AtlasPreferences.getTokenExpireDate(context);
    if (expireDate == null) {
      return false;
    }
    Date current = new Date();
    long expiresIn = (expireDate.getTime() - current.getTime()) / (1000 * 60 * 60 * 24);
    long expireDelta = EXPIRE_REFRESH_DELTA;
    boolean expired = expiresIn < expireDelta;

    Log.d(TAG, "Token expires in: " + expiresIn);
    return expired;
  }

  public static JSONObject atlasRefreshToken(Context context) {
    JSONObject result = new JSONObject();
    try {
      JSONObject obj = new JSONObject();
      obj.put("token", AtlasPreferences.getRegisteredKey(context));
      result = fetchResource(context, "POST", API_TOKEN_REFRESH, obj);
      if (result.has("token")) {
        Log.w(TAG, "Token refresh. New token issued.");
        String token = result.getString("token");
        AtlasPreferences.setRegisteredAtlas(context, token);
      }
    } catch (Exception e) {
      e.printStackTrace();
      Log.e(TAG, "Atlas RefreshToken failed");
    }
    return result;
  }

  public static JSONObject resetPassword(Context context, String tag, String org) {
    String host = BuildConfig.FORSTA_API_URL;
    JSONObject resetBody = new JSONObject();
    try {
      resetBody.put("fq_tag", "@" + tag + ":" + org);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    JSONObject result = NetworkUtils.apiFetch("POST", null, host + API_USER_RESET_PASSWORD, resetBody);
    return result;
  }

  public static void syncAtlasContacts(Context context) {
    syncAtlasContacts(context, false);
  }

  public static void syncAtlasContacts(Context context, boolean removeInvalidUsers) {
    try {
      AtlasOrg org = AtlasOrg.getLocalForstaOrg(context);
      if (org.getSlug().equals("public") || org.getSlug().equals("forsta")) {
        List<String> addresses = DbFactory.getThreadDatabase(context).getAllRecipients();
        List<AtlasUser> threadContacts = new ArrayList<>();
        if (addresses.size() > 0) {
          JSONObject threadUsers = getUserDirectory(context, addresses);
          threadContacts = AtlasApi.parseUsers(context, threadUsers);
        }
        if (Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
          List<AtlasUser> knownLocalContacts = getKnownLocalContacts(context);
          for (AtlasUser user : knownLocalContacts) {
            if (!threadContacts.contains(user)) {
              threadContacts.add(user);
            }
          }
        }
        syncAtlasContactsDb(context, threadContacts, removeInvalidUsers);
      } else {
        JSONObject orgUsers = getOrgUsers(context);
        List<AtlasUser> orgContacts = parseUsers(context, orgUsers);
        List<String> addresses = DbFactory.getThreadDatabase(context).getAllRecipients();
        for (AtlasUser user : orgContacts) {
          if (addresses.contains(user.getUid())) {
            addresses.remove(user.getUid());
          }
        }
        if (addresses.size() > 0) {
          JSONObject threadUsers = getUserDirectory(context, addresses);
          List<AtlasUser> threadContacts = parseUsers(context, threadUsers);
          for (AtlasUser user : threadContacts) {
            addresses.remove(user.getUid());
          }
          if (addresses.size() > 0) {
            DbFactory.getContacts(context).setInactiveAddresses(addresses);
          }
          orgContacts.addAll(threadContacts);
        }
        syncAtlasContactsDb(context, orgContacts, removeInvalidUsers);
        syncOrgTags(context);
      }
      AtlasPreferences.setForstaContactSync(context, new Date().getTime());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void syncAtlasContacts(Context context, List<String> addresses) {
    JSONObject response = getUserDirectory(context, addresses);
    List<AtlasUser> forstaContacts = parseUsers(context, response);
    syncAtlasContactsDb(context, forstaContacts, false);
  }

  private static List<AtlasUser> getKnownLocalContacts(Context context) {
    Set<String> systemNumbers = new HashSet<>();
    Cursor cursor = null;
    try {
      cursor = DbFactory.getContacts(context).querySystemContacts("");
      while (cursor != null && cursor.moveToNext()) {
        String number = cursor.getString(cursor.getColumnIndex(Contacts.NUMBER_COLUMN));
        try {
          number = Util.canonicalizeNumberE164(number);
          systemNumbers.add(number);
        } catch (InvalidNumberException e) {
          e.printStackTrace();
        }
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

    return getAtlasUsersByPhone(context, systemNumbers);
  }

  private static void syncAtlasContactsDb(Context context, List<AtlasUser> contacts, boolean removeExisting) {
    DbFactory.getContacts(context).updateUsers(contacts, removeExisting);
  }

  private static void syncOrgTags(Context context) {
    JSONObject response = getTags(context);
    List<AtlasTag> groups = parseTagGroups(response);
    GroupDatabase db = DbFactory.getGroupDatabase(context);
    db.updateGroups(groups);
  }

  private static JSONObject getUsersByPhone(Context context, Set<String> phoneNumbers) {
    String query = "";
    try {
      if (!phoneNumbers.isEmpty()) {
        query = "?phone_in=" + URLEncoder.encode(TextUtils.join(",", phoneNumbers), "UTF-8");
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return fetchResource(context, "GET", API_DIRECTORY_USER + query);
  }

  private static JSONObject getUsersByEmail(Context context, Set<String> emailAddresses) {
    String query = "";
    try {
      query = "?email_in=" + URLEncoder.encode(TextUtils.join(",", emailAddresses), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return fetchResource(context, "GET", API_DIRECTORY_USER + query);
  }

  public static JSONObject getUserDirectory(Context context, List<String> addresses) {
    String addressesString = TextUtils.join(",", addresses);
    String query = "";
    if (!TextUtils.isEmpty(addressesString)) {
      query = "?id_in=" + addressesString;
    }
    return fetchResource(context, "GET", API_DIRECTORY_USER + query);
  }

  public static JSONObject getOrgDirectory(Context context) {
    return fetchResource(context, "GET", API_DIRECTORY_DOMAIN);
  }

  public static JSONObject getOrg(Context context) {
    AtlasUser localAccount = AtlasUser.getLocalUser(context);
    if (localAccount == null) {
      return null;
    }
    return getOrg(context, localAccount.org_id);
  }

  private static JSONObject getOrg(Context context, String id) {
    return fetchResource(context, "GET", API_ORG + id + "/");
  }

  public static JSONObject getOrgUsers(Context context) {
    return fetchResource(context, "GET", API_USER);
  }

  public static JSONObject getLocalForstaUser(Context context) {
    return fetchResource(context, "GET", API_USER + TextSecurePreferences.getLocalAddress(context) + "/");
  }

  public static JSONObject getTags(Context context) {
    return fetchResource(context, "GET", API_TAG);
  }

  public static JSONObject getUserPick(Context context) {
    return fetchResource(context, "GET", API_USER_PICK);
  }

  public static JSONObject getTagPick(Context context) {
    return fetchResource(context, "GET", API_TAG_PICK);
  }

  public static JSONObject getTagMath(Context context, String expression) {
    JSONObject obj = new JSONObject();
    try {
      JSONArray expressions = new JSONArray();
      expressions.put(expression);
      obj.put("expressions", expressions);
      return fetchResource(context, "POST", API_TAGMATH, obj);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return obj;
  }

  public static JSONObject getRtcServers(Context context) {
    return fetchResource(context, "GET", API_RTC);
  }

  public static JSONObject getDistribution(Context context, String expression) {
    JSONObject jsonObject = new JSONObject();
    JSONObject response = new JSONObject();
    try {
      jsonObject.put("expression", expression);
      String urlEncoded = TextUtils.isEmpty(expression) ? "" : URLEncoder.encode(expression, "UTF-8");
      response = fetchResource(context, "GET", API_DIRECTORY_USER + "?expression=" + urlEncoded);
    } catch (JSONException e) {
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return response;
  }

  public static JSONObject searchUserDirectory(Context context, String searchText) {
    JSONObject response = new JSONObject();
    try {
      String urlEncoded = TextUtils.isEmpty(searchText) ? "" : URLEncoder.encode(searchText, "UTF-8");
      response = fetchResource(context, "GET", API_DIRECTORY_USER + "?q=" + urlEncoded);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return response;
  }

  private static JSONObject fetchResource(Context context, String method, String urn) {
    return fetchResource(context, method, urn, null);
  }

  private static JSONObject fetchResource(Context context, String method, String urn, JSONObject body) {
    String baseUrl = BuildConfig.FORSTA_API_URL;
    String authKey = AtlasPreferences.getRegisteredKey(context);
    return NetworkUtils.apiFetch(method, authKey, baseUrl + urn, body);
  }

  private static JSONObject hardFetchResource(Context context, String method, String urn, JSONObject body) throws Exception {
    String baseUrl = BuildConfig.FORSTA_API_URL;
    String authKey = AtlasPreferences.getRegisteredKey(context);
    return NetworkUtils.apiHardFetch(method, authKey, baseUrl + urn, body);
  }

  // Helper methods and mapper functions. Move these.
  public static String parseLoginToken(String authtoken) {
    if (authtoken.contains("/")) {
      String[] parts = authtoken.split("/");
      authtoken = parts[parts.length - 1];
    }
    return authtoken;
  }

  public static List<AtlasUser> parseUsers(Context context, JSONObject jsonObject) {
    List<AtlasUser> users = new ArrayList<>();
    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        try {
          JSONObject user = results.getJSONObject(i);
          AtlasUser atlasUser = new AtlasUser(user);
          users.add(atlasUser);
        } catch (Exception e) {
          Log.e(TAG, "parseUsers exception: " + e.getMessage());
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "No results array.");
    }
    return users;
  }

  public static List<AtlasTag> parseTagGroups(JSONObject jsonObject) {
    List<AtlasTag> groups = new ArrayList<>();

    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        JSONObject result = results.getJSONObject(i);
        JSONArray users = result.getJSONArray("users");
        // TODO Right now, not getting tags with no members. Leaves only.
        Set<String> members = new HashSet<>();
        boolean isGroup = false;
        for (int j = 0; j < users.length(); j++) {
          JSONObject userObj = users.getJSONObject(j);
          String association = userObj.getString("association_type");

          if (association.equals("MEMBEROF")) {
            isGroup = true;
            JSONObject user = userObj.getJSONObject("user");
            String userId = user.getString("id");
            members.add(userId);
          }
        }
        if (isGroup) {
          AtlasTag group = new AtlasTag(result);
          group.addMembers(members);
          groups.add(group);
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "parseTagGroups exception");
      e.printStackTrace();
    }
    return groups;
  }

  public static RelayDistribution getMessageDistribution(Context context, String expression) {
    JSONObject response = getTagMath(context, expression);
    try {
      JSONArray results = response.getJSONArray("results");
      if (results.length() > 0) {
        return RelayDistribution.fromJson(results.getJSONObject(0));
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return new RelayDistribution();
  }

  public static List<AtlasUser> getAtlasUsersByPhone(Context context, Set<String> phoneNumbers) {
    JSONObject jsonObject = getUsersByPhone(context, phoneNumbers);
    return parseUsers(context, jsonObject);
  }

  public static List<AtlasUser> getAtlasUsersByEmail(Context context, Set<String> emailAddresses) {
    JSONObject jsonObject = getUsersByEmail(context, emailAddresses);
    return parseUsers(context, jsonObject);
  }

  public static boolean isErrorResponse(JSONObject response) {
    if (response.has("error")) {
      return true;
    }
    return false;
  }

  public static boolean isUnauthorizedResponse(JSONObject response) {
    if (response == null) {
      return true;
    }
    if (isErrorResponse(response)) {
      try {
        String error = response.getString("error");
        Log.e(TAG, error);
        if (error.contains("401") || error.contains("400")) {
          Log.e(TAG, "CCSM API Unauthorized or Expired.");
          return true;
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return false;
  }
}
