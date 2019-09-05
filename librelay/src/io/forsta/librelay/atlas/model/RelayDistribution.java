package io.forsta.librelay.atlas.model;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

/**
 * Created by jlewis on 9/6/17.
 */

public class RelayDistribution {
  private static final String TAG = RelayDistribution.class.getSimpleName();
  public String pretty;
  public String universal;
  public Set<String> userIds = new HashSet<>();
  public Set<String> monitorIds = new HashSet<>();
  public String warning = "";

  public RelayDistribution() {

  }

  public static @NonNull RelayDistribution fromJson(JSONObject jsonResponse) {
    RelayDistribution relayDistribution = new RelayDistribution();
    try {
      JSONArray ids = jsonResponse.getJSONArray("userids");
      for (int i=0; i<ids.length(); i++) {
        relayDistribution.userIds.add(ids.getString(i));
      }
      if (jsonResponse.has("monitorids")) {
        JSONArray mIds = jsonResponse.getJSONArray("monitorids");
        for (int i=0; i< mIds.length(); i++) {
          relayDistribution.monitorIds.add(mIds.getString(i));
        }
      }
      relayDistribution.universal = jsonResponse.getString("universal");
      relayDistribution.pretty = jsonResponse.getString("pretty");

      JSONArray warnings = jsonResponse.getJSONArray("warnings");
      StringBuilder sb = new StringBuilder();
      for (int i=0; i<warnings.length(); i++) {
        JSONObject object = warnings.getJSONObject(i);
        if (object.has("kind")) {
          sb.append(object.getString("kind")).append(": ");
        }
        if (object.has("cue")) {
          sb.append(object.getString("cue"));
        }
      }
      relayDistribution.appendWarning(sb.toString());
    } catch (JSONException e) {
      Log.w(TAG, "RelayDistribution json parsing error!");
      Log.w(TAG, "Distribution object:");
      Log.w(TAG, jsonResponse.toString());
      e.printStackTrace();
      relayDistribution.appendWarning("Bad response from server");
    }
    return relayDistribution;
  }

  public boolean isValid() {
    return universal != null && universal.contains("<") && hasRecipients();
  }

  public boolean hasRecipients() {
    return userIds.size() > 0;
  }

  public boolean hasMonitors() {
    return monitorIds.size() > 0;
  }

  public List<String> getRecipients(Context context) {
    List<String> users = new ArrayList<>();
    boolean excludeSelf = true;
    if (userIds.size() > 2 || userIds.size() == 1) {
      excludeSelf = false;
    }
    for (String id : userIds) {
      if (!(excludeSelf && id.equals(TextSecurePreferences.getLocalAddress(context)))) {
        users.add(id);
      }
    }
    return users;
  }

  public String getSortedAddresses() {
    TreeSet<String> addresses = new TreeSet<>();
    for (String address : userIds) {
      addresses.add(address);
    }
    return Util.join(addresses.toArray(new String[addresses.size()]), ",");
  }

  public List<String> getMonitors(Context context) {
    return new ArrayList<>(monitorIds);
  }

  public boolean hasWarnings() {
    return !TextUtils.isEmpty(warning);
  }

  public String getWarnings() {
    return warning;
  }

  private void appendWarning(String warningMessage) {
    if (!hasWarnings()) {
      warning = warningMessage;
    } else {
      warning += " " + warningMessage;
    }
  }
}
