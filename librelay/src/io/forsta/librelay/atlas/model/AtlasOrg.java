package io.forsta.librelay.atlas.model;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.librelay.atlas.AtlasPreferences;

/**
 * Created by jlewis on 9/21/17.
 */

public class AtlasOrg {
  private static final String TAG = AtlasOrg.class.getSimpleName();
  private String uid;
  private String name;
  private String slug;
  private boolean offTheRecord = false;

  public AtlasOrg() {

  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getUid() {
    return uid;
  }

  public static AtlasOrg fromJsonString(String jsonString) {
    try {
      JSONObject json = new JSONObject(jsonString);
      AtlasOrg org = new AtlasOrg();
      org.uid = json.getString("id");
      org.name = json.getString("name");
      org.slug = json.getString("slug");
      if (json.has("preferences")) {
        JSONObject preferences = json.getJSONObject("preferences");
        if (preferences.has("messaging.off_the_record")) {
          org.offTheRecord = preferences.getBoolean("messaging.off_the_record");
        }
      }
      return org;
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static AtlasOrg getLocalForstaOrg(Context context) {
    return fromJsonString(AtlasPreferences.getForstaOrg(context));
  }
}
