package io.forsta.librelay.atlas.model;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.util.InvalidUserException;

/**
 * Created by jlewis on 3/2/17.
 */

public class AtlasUser {
  private static final String TAG = AtlasUser.class.getSimpleName();
  public String uid;
  public String tag_id;
  public String name;
  public String username;
  public String slug;
  public String email;
  public String avatar;
  public String phone;
  public String org_id;
  public String org_slug;
  public boolean tsRegistered = false;
  public boolean isActive = true;
  public boolean isMonitor = false;
  public RecipientType type = RecipientType.PERSON;
  public boolean has_password = false;

  public enum RecipientType {
    PERSON,
    BOT,
    EPHEMERAL
  }

  public AtlasUser() {

  }

  public AtlasUser(JSONObject userObj) throws InvalidUserException {
    try {
      if (userObj.has("is_monitor")) {
        this.isMonitor = userObj.getBoolean("is_monitor");
      }
      if (userObj.has("is_active")) {
        this.isActive = userObj.getBoolean("is_active");
      }
      if (userObj.has("user_type")) {
        String userType = userObj.getString("user_type");
        switch (userType) {
          case "PERSON":
            this.type = RecipientType.PERSON;
            break;
          case "BOT":
            this.type = RecipientType.BOT;
            break;
          case "EPHEMERAL":
            this.type = RecipientType.EPHEMERAL;
            break;
        }
      }
      this.uid = userObj.getString("id");
      if (TextUtils.isEmpty(this.uid)) {
        throw new InvalidUserException("ADDRESS is empty of null");
      }
      String name = getFullName(userObj);
      this.name = name;
      if (userObj.has("tag")) {
        JSONObject tag = userObj.getJSONObject("tag");
        if (tag.has("id")) {
          this.tag_id = tag.getString("id");
        }
        if (tag.has("slug")) {
          this.slug = tag.getString("slug");
          this.username = this.slug;
        }
      }
      JSONObject org = userObj.getJSONObject("org");
      if (org.has("id")) {
        this.org_id = org.getString("id");
      }

      if (org.has("slug")) {
        this.org_slug = org.getString("slug");
      }

      if (userObj.has("gravatar_hash")) {
        this.avatar = userObj.getString("gravatar_hash");
      }

      if (userObj.has("email")) {
        this.email = userObj.getString("email");
      }

      if (userObj.has("phone")) {
        this.phone = userObj.getString("phone");
      }
      this.tsRegistered = false;
      if (userObj.has("has_password")) {
        this.has_password = userObj.getBoolean("has_password");
      }

    } catch (JSONException e) {
      e.printStackTrace();
      Log.w(TAG, userObj.toString());
      throw new InvalidUserException("An error occured parsing user JSON");
    }
  }

  // TODO Return Recipient object from DB unless using this for pure Atlas purposes.
  public static AtlasUser getLocalUser(Context context) {
    try {
      return new AtlasUser(new JSONObject(AtlasPreferences.getForstaUser(context)));
    } catch (JSONException e) {
      Log.e(TAG, "Exception parsing user object from preferences");
    } catch (InvalidUserException e) {
      Log.e(TAG, e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

  public String getName() {
    return name;
  }

  public String getUid() {
    return uid;
  }

  private String getFullName(JSONObject userObject) throws JSONException {
    StringBuilder name = new StringBuilder();
    String firstName = userObject.getString("first_name");
    String middleName = userObject.has("middle_name") ? userObject.getString("middle_name") : "";
    String lastName = userObject.getString("last_name");
    name.append(firstName).append(" ");
    if (!middleName.equals("")) {
      name.append(middleName).append(" ");
    }
    name.append(lastName);
    return name.toString();
  }

  public String getOrgTag() {
    return org_slug;
  }

  public String getTag() {
    return slug;
  }

  public String getFullTag() {
    return slug + ":" + org_slug;
  }

  public String getPhone() {
    return phone;
  }

  public String getAvatar() {
    return avatar;
  }

  @Override
  public int hashCode() {
    return 31 + uid.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof AtlasUser)) return false;
    AtlasUser other = (AtlasUser)o;
    return this.uid.equals(other.uid);
  }
}
