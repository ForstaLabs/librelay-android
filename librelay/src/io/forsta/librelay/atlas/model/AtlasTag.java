package io.forsta.librelay.atlas.model;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;


/**
 * Created by jlewis on 2/24/17.
 */

public class AtlasTag {
  private static final String TAG = AtlasTag.class.getSimpleName();
  public String uid;
  public String slug;
  public String org_id;
  public String org_slug;
  public String description;
  public String parent;
  public Set<String> members = new HashSet<>();

  public AtlasTag(JSONObject jsonObject) {
    try {
      this.uid = jsonObject.getString("id");
      this.slug = jsonObject.getString("slug");
      this.description = jsonObject.getString("description");
      JSONObject orgObj = jsonObject.getJSONObject("org");
      if (orgObj.has("id")) {
        this.org_id = orgObj.getString("id");
      }
      this.org_slug = orgObj.getString("slug");
    } catch (JSONException e) {
      Log.w(TAG, "Error parsing tag");
      e.printStackTrace();
    }
  }

  public void addMembers(Set<String> numbers) {
    for (String number : numbers) {
      members.add(number);
    }
  }

  public String getUid() {
    return uid;
  }
}
