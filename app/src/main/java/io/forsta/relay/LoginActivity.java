package io.forsta.relay;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.StringTokenizer;
import java.util.concurrent.Executor;

import io.forsta.relay.BaseActionBarActivity;
import io.forsta.relay.ConversationListActivity;

import io.forsta.librelay.BuildConfig;
import io.forsta.librelay.atlas.AtlasApi;
import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.permissions.Permissions;
import io.forsta.librelay.util.ForstaUtils;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

public class LoginActivity extends BaseActionBarActivity implements Executor {
  private static final String TAG = LoginActivity.class.getSimpleName();
  private Handler handler = new Handler(Looper.getMainLooper());

  private LinearLayout mLoginFormContainer;
  private LinearLayout mSendLinkFormContainer;
  private LinearLayout mVerifyFormContainer;
  private LinearLayout passwordAuthContainer;
  private LinearLayout mAccountFormContainer;
  private TextView mLoginTitle;
  private EditText mSendTokenUsername;
  private EditText mLoginSecurityCode;
  private EditText mAccountFullName;
  private EditText mAccountTagSlug;
  private EditText mAccountPhone;
  private EditText mAccountEmail;
  private EditText mAccountPassword;
  private EditText mAccountPasswordVerify;
  private EditText mPassword;
  private EditText totp;
  private LinearLayout totpContainer;
  private ProgressBar mLoginProgressBar;
  private LinearLayout createAccountContainer;
  private LinearLayout tryAgainContainer;
  private TextView tryAgainMessage;
  private TextView errorMessage;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME);
    initializeView();
    initializeListeners();
    initializePermissions();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!TextUtils.isEmpty(AtlasPreferences.getForstaUsername(this))) {
      mSendTokenUsername.setText(AtlasPreferences.getForstaUsername(this) + ":" + AtlasPreferences.getForstaOrgName(this));
    }
    if (AtlasPreferences.getForstaLoginPending(LoginActivity.this)) {
      showVerifyForm();
    } else if (!AtlasPreferences.getRegisteredKey(LoginActivity.this).equals("")) {
      Intent intent = new Intent(LoginActivity.this, ConversationListActivity.class);
      startActivity(intent);
      finish();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  private void initializeView() {
    mLoginTitle = (TextView) findViewById(R.id.forsta_login_title);
    tryAgainMessage = (TextView) findViewById(R.id.forsta_login_tryagain_message);
    mLoginProgressBar = (ProgressBar) findViewById(R.id.login_progress_bar);

    mLoginFormContainer = (LinearLayout) findViewById(R.id.forsta_login_container);
    mSendLinkFormContainer = (LinearLayout) findViewById(R.id.forsta_login_send_link_container);
    mVerifyFormContainer = (LinearLayout) findViewById(R.id.forsta_login_verify_token_container);
    mAccountFormContainer = (LinearLayout) findViewById(R.id.forsta_login_create_account_container);
    passwordAuthContainer = (LinearLayout) findViewById(R.id.forsta_login_password_container);

    mSendTokenUsername = (EditText) findViewById(R.id.forsta_login_username_get_token);
    mLoginSecurityCode = (EditText) findViewById(R.id.forsta_login_security_code);
    mAccountFullName = (EditText) findViewById(R.id.forsta_login_account_fullname);
    mAccountTagSlug = (EditText) findViewById(R.id.forsta_login_account_tag_slug);
    mAccountPhone = (EditText) findViewById(R.id.forsta_login_account_phone);
    mAccountEmail = (EditText) findViewById(R.id.forsta_login_account_email);
    mAccountPassword = (EditText) findViewById(R.id.forsta_login_account_password);
    mAccountPasswordVerify = (EditText) findViewById(R.id.forsta_login_account_password_verify);
    mPassword = (EditText) findViewById(R.id.forsta_login_password);
    totpContainer = (LinearLayout) findViewById(R.id.forsta_login_totp_container);
    totp = (EditText) findViewById(R.id.forsta_login_totp);
    errorMessage = (TextView) findViewById(R.id.forsta_login_error);

    createAccountContainer = (LinearLayout) findViewById(R.id.create_account_button_container);
    tryAgainContainer = (LinearLayout) findViewById(R.id.forsta_login_tryagain_container);

    String phone = TextSecurePreferences.getLocalAddress(LoginActivity.this);
    if (!phone.equals("No Stored Number")) {
      mAccountPhone.setText(phone);
    }

    AtlasUser user = AtlasUser.getLocalUser(LoginActivity.this);
    if (user != null) {
      createAccountContainer.setVisibility(View.GONE);
    }
  }

  private void initializePermissions() {
    Permissions.with(LoginActivity.this)
        .request(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .ifNecessary()
        .withRationaleDialog(getString(io.forsta.librelay.R.string.Permissions_required_initial))
        .execute();
  }

  private void initializeListeners() {
    mAccountFullName.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void afterTextChanged(Editable editable) {
        String slug = ForstaUtils.slugify(editable.toString());
        mAccountTagSlug.setText(slug);
      }
    });

    Button getTokenButton = (Button) findViewById(R.id.forsta_get_token_button);
    getTokenButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Pair<String, String> userNameOrg = getUsernameAndOrg(mSendTokenUsername.getText().toString());
        String username = userNameOrg.first;
        String org = userNameOrg.second;
        if (org.length() < 1 || username.length() < 1) {
          Toast.makeText(LoginActivity.this, "Invalid Organization or Username", Toast.LENGTH_LONG).show();
        } else {
          showProgressBar();
          AtlasPreferences.setForstaOrgName(getApplicationContext(), org);
          AtlasPreferences.setForstaUsername(getApplicationContext(), username);
          CCSMSendToken getToken = new CCSMSendToken();
          getToken.execute(org, username);
        }
      }
    });

    Button submitTokenButton = (Button) findViewById(R.id.forsta_login_submit_button);
    submitTokenButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String code = mLoginSecurityCode.getText().toString().trim();
        if (code.length() != 6) {
          Toast.makeText(LoginActivity.this, "Invalid security code", Toast.LENGTH_LONG).show();
        } else {
          showProgressBar();
          CCSMLogin task = new CCSMLogin(true);
          task.execute(code);
        }
      }
    });

    Button createAccountButton = (Button) findViewById(R.id.forsta_create_account_button);
    createAccountButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showAccountForm();
      }
    });

    Button submitAccountButton = (Button) findViewById(R.id.forsta_login_account_submit_button);
    submitAccountButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showProgressBar();
        String errorMessage = validateAccountForm();
        if (errorMessage != null) {
          hideProgressBar();
          showError(errorMessage);
          return;
        }

        SafetyNet.getClient(LoginActivity.this).verifyWithRecaptcha(BuildConfig.RECAPTCHA_KEY)
            .addOnSuccessListener((Activity) LoginActivity.this,
                new OnSuccessListener<SafetyNetApi.RecaptchaTokenResponse>() {
                  @Override
                  public void onSuccess(SafetyNetApi.RecaptchaTokenResponse response) {
                    // Indicates communication with reCAPTCHA service was
                    // successful.
                    final String userResponseToken = response.getTokenResult();
                    if (!userResponseToken.isEmpty()) {
                      handler.post(new Runnable() {
                        @Override
                        public void run() {
                          joinForsta(userResponseToken);
                        }
                      });
                    }
                  }
                })
            .addOnFailureListener((Activity) LoginActivity.this, new OnFailureListener() {
              @Override
              public void onFailure(final @NonNull Exception e) {
                if (e instanceof ApiException) {
                  // An error occurred when communicating with the
                  // reCAPTCHA service. Refer to the status code to
                  // handle the error appropriately.
                  ApiException apiException = (ApiException) e;
                  int statusCode = apiException.getStatusCode();
                  Log.d(TAG, "Error: " + CommonStatusCodes
                      .getStatusCodeString(statusCode));
                } else {
                  // A different, unknown type of error occurred.
                  Log.d(TAG, "Error: " + e.getMessage());
                }
                handler.post(new Runnable() {
                  @Override
                  public void run() {
                    joinForstaFail(e);
                  }
                });
              }
            });
      }
    });

    Button cancelAccountButton = (Button) findViewById(R.id.forsta_login_account_cancel_button);
    cancelAccountButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showSendLinkForm();
      }
    });

    TextView tryAgainText = (TextView) findViewById(R.id.forsta_login_tryagain);
    tryAgainText.setPaintFlags(tryAgainText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    tryAgainText.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AtlasPreferences.clearLogin(LoginActivity.this);
        mLoginSecurityCode.setText("");
        showSendLinkForm();
      }
    });

    TextView resetPasswordText = (TextView) findViewById(R.id.forsta_login_forgotpassword_link);
    resetPasswordText.setPaintFlags(resetPasswordText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    resetPasswordText.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showProgressBar();
        Pair<String, String> userNameAndOrg = getUsernameAndOrg(mSendTokenUsername.getText().toString());
        ResetPasswordTask resetPasswordTask = new ResetPasswordTask();
        resetPasswordTask.execute(userNameAndOrg.first, userNameAndOrg.second);
      }
    });

    Button loginWithPasswordButton = (Button) findViewById(R.id.forsta_login_password_submit_button);
    loginWithPasswordButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (TextUtils.isEmpty(mPassword.getText().toString())) {
          Toast.makeText(LoginActivity.this, "Please enter a password.", Toast.LENGTH_LONG).show();
          return;
        }
        showProgressBar();
        CCSMLogin loginTask = new CCSMLogin(false);
        loginTask.execute(mPassword.getText().toString(), totp.getText().toString());
      }
    });
  }

  private void joinForstaFail(Exception e) {
    hideProgressBar();
    showError(e.getMessage() + "");
  }

  private String validateAccountForm() {
    String fullName = mAccountFullName.getText().toString().trim();
    String tagSlug = mAccountTagSlug.getText().toString().trim();
    String phone = mAccountPhone.getText().toString().trim();
    String email = mAccountEmail.getText().toString().trim();
    String password = mAccountPassword.getText().toString().trim();
    String passwordVerify = mAccountPasswordVerify.getText().toString().trim();
    try {
      if (fullName.length() < 1) {
        throw new Exception("Please enter a name");
      }

      if (tagSlug.length() < 1) {
        throw new Exception("Please enter a username");
      }

      if (phone.length() < 10) {
        throw new InvalidNumberException("Phone number Too short");
      }

      String formattedPhone = Util.canonicalizeNumberE164(phone);
      mAccountPhone.setText(formattedPhone);

      if (email.length() < 8) {
        throw new Exception("Please enter a valid email");
      }

      if (password.length() < 8) {
        throw new Exception("Please enter a valid password. It must be at least 8 characters");
      }

      if (!password.equals(passwordVerify)) {
        throw new Exception("Passwords don't match.");
      }

    } catch (InvalidNumberException | Exception e) {
      String message = e.getMessage();
      Log.d(TAG, "Join Form validation error: " + message);
      return message;
    }
    return null;
  }

  private void joinForsta(String captcha) {
    hideError();
    String fullName = mAccountFullName.getText().toString().trim();
    String tagSlug = mAccountTagSlug.getText().toString().trim();
    String phone = mAccountPhone.getText().toString().trim();
    String email = mAccountEmail.getText().toString().trim();
    String password = mAccountPassword.getText().toString().trim();

    JoinAccountTask joinTask = new JoinAccountTask();
    joinTask.execute(fullName, tagSlug, phone, email, password, captcha);
  }

  private void showSendLinkForm() {
    AtlasPreferences.setForstaLoginPending(LoginActivity.this, false);
    mPassword.setText("");
    hideError();
    mVerifyFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.GONE);
    tryAgainContainer.setVisibility(View.GONE);
    mSendLinkFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showVerifyForm() {
    hideError();
    mSendLinkFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.VISIBLE);
    tryAgainContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showAuthPassword(boolean totpAuth) {
    AtlasPreferences.setForstaLoginPending(LoginActivity.this, false);
    hideError();
    mVerifyFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    mSendLinkFormContainer.setVisibility(View.GONE);
    if (totpAuth) {
      totpContainer.setVisibility(View.VISIBLE);
    } else {
      totpContainer.setVisibility(View.GONE);
    }
    passwordAuthContainer.setVisibility(View.VISIBLE);
    tryAgainContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showAccountForm() {
    AtlasPreferences.setForstaLoginPending(LoginActivity.this, false);
    hideError();
    mLoginTitle.setText(io.forsta.librelay.R.string.forsta_login_title_join);
    mSendLinkFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.GONE);
    tryAgainContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showError() {
    showError("Invalid server response.");
  }

  private void showError(String error) {
    errorMessage.setText(error);
    errorMessage.setVisibility(View.VISIBLE);
  }

  private void hideError() {
    errorMessage.setText("");
    errorMessage.setVisibility(View.GONE);
  }

  private void showProgressBar() {
    hideError();
    mLoginFormContainer.setVisibility(View.GONE);
    mLoginProgressBar.setVisibility(View.VISIBLE);
  }

  private void hideProgressBar() {
    mLoginFormContainer.setVisibility(View.VISIBLE);
    mLoginProgressBar.setVisibility(View.GONE);
  }

  private void finishLoginActivity() {
    Intent nextIntent = getIntent().getParcelableExtra("next_intent");
    if (nextIntent == null) {
      nextIntent = new Intent(LoginActivity.this, ConversationListActivity.class);
    }

    startActivity(nextIntent);
    finish();
  }

  private Pair<String, String> getUsernameAndOrg(String tag) {
    StringTokenizer tokenizer = new StringTokenizer(tag.trim(), ":");
    String username = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
    String org = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
    return new Pair(username, org);
  }

  @Override
  public void execute(@NonNull Runnable command) {
    command.run();
  }

  private class CCSMSendToken extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... params) {
      String org = params[0];
      String uname = params[1];
      JSONObject response = AtlasApi.atlasSendToken(getApplicationContext(), org.trim(), uname.trim());
      return response;
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      try {
        if (jsonObject.has("msg")) {
          AtlasPreferences.setForstaLoginPending(LoginActivity.this, true);
          showVerifyForm();
          Toast.makeText(LoginActivity.this, jsonObject.getString("msg"), Toast.LENGTH_LONG).show();
        } else {
          if (jsonObject.has("error")) {
            String errorResult = jsonObject.getString("error");
            JSONObject error = new JSONObject(errorResult);
            if (error.has("409")) {
              boolean totpAuth = false;
              AtlasPreferences.setForstaLoginPending(LoginActivity.this, true);
              JSONObject errors = error.getJSONObject("409");
              if (errors.has("non_field_errors")) {
                JSONArray nfes = errors.getJSONArray("non_field_errors");
                for (int i=0; i<nfes.length(); i++) {
                  if (nfes.getString(i).equals("totp auth required")) {
                    totpAuth = true;
                  }
                }
              }
              showAuthPassword(totpAuth);
            } else {
              String messages = ForstaUtils.parseErrors(error);
              hideProgressBar();
              showError(messages);
            }
          } else {
            hideProgressBar();
            showError();
          }
        }
      } catch (JSONException e) {
        Log.d(TAG, e.getMessage());
        e.printStackTrace();
        hideProgressBar();
        showError();
      }
    }
  }

  private class CCSMLogin extends AsyncTask<String, Void, JSONObject> {
    private boolean isSms = true;

    public CCSMLogin(boolean isSms) {
      this.isSms = isSms;
    }

    @Override
    protected JSONObject doInBackground(String... params) {
      JSONObject authObj = new JSONObject();
      try {
        String auth = params[0];
        if (params.length > 1 && !TextUtils.isEmpty(params[1])) {
          authObj.put("otp", params[1]);
        }
        String username = AtlasPreferences.getForstaUsername(getApplicationContext());
        String org = AtlasPreferences.getForstaOrgName(getApplicationContext());
        if (isSms) {
          auth = org + ":" + username + ":" + auth;
          authObj.put("authtoken", auth);
        } else {
          authObj.put("fq_tag", username + ":" + org);
          authObj.put("password", auth);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      //TODO See if token contains org information.
      JSONObject token = AtlasApi.atlasLogin(LoginActivity.this, authObj);
      return token;
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      Context context = LoginActivity.this;

      try {
        if (jsonObject.has("token")) {
          String token = jsonObject.getString("token");
          JSONObject user = jsonObject.getJSONObject("user");
          AtlasUser currentUser = AtlasUser.getLocalUser(context);
          if (currentUser != null) {
            if (!currentUser.getUid().equals(user.getString("id"))) {
              hideProgressBar();
              showError("Invalid User. To login as a different user, you must reinstall.");
              return;
            }
          }
          Log.w(TAG, "Login Success. Token Received.");

          AtlasPreferences.setForstaUser(context, user.toString());

          AtlasPreferences.setRegisteredAtlas(context, token);
          AtlasPreferences.setForstaLoginPending(context, false);
          finishLoginActivity();
        } else if (jsonObject.has("error")) {
          String errorResult = jsonObject.getString("error");
          String messages = ForstaUtils.parseErrors(new JSONObject(errorResult));
          hideProgressBar();
          showError(messages);
        } else {
          hideProgressBar();
          showError();
        }
      } catch (JSONException e) {
        hideProgressBar();
        showError();
      }
    }
  }

  private class JoinAccountTask extends AsyncTask<String, Void, JSONObject> {
    @Override
    protected JSONObject doInBackground(String... strings) {
      JSONObject jsonObject = new JSONObject();
      try {
        jsonObject.put("fullname", strings[0]);
        jsonObject.put("tag_slug", strings[1]);
        jsonObject.put("phone", strings[2]);
        jsonObject.put("email", strings[3]);
        jsonObject.put("password", strings[4]);
        jsonObject.put("captcha", strings[5]);
      } catch (JSONException e) {
        e.printStackTrace();
      }
      return AtlasApi.accountJoin(jsonObject);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      try {
        if (jsonObject.has("jwt")) {
            String username = jsonObject.getString("nametag");
            String org = jsonObject.getString("orgslug");
            String jwt = jsonObject.getString("jwt");
            AtlasPreferences.setRegisteredAtlas(LoginActivity.this, jwt);
            AtlasPreferences.setForstaUsername(LoginActivity.this, username);
            AtlasPreferences.setForstaOrgName(LoginActivity.this, org);
            hideProgressBar();
            finishLoginActivity();
        } else if (jsonObject.has("error")) {
          String errorResult = jsonObject.getString("error");
          String messages = ForstaUtils.parseErrors(new JSONObject(errorResult));
          hideProgressBar();
          showError(messages);
        } else {
          hideProgressBar();
          showError();
        }
      } catch (JSONException e) {
        hideProgressBar();
        showError();
      }
    }
  }

  private class ResetPasswordTask extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... strings) {
      return AtlasApi.resetPassword(LoginActivity.this, strings[0], strings[1]);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      if (jsonObject.has("method")) {
        String method = jsonObject.optString("method", "unknown");
        hideProgressBar();
        handleResetSuccess(method);
      } else {
        hideProgressBar();
        showError("Unable to reset password.");
      }
    }
  }

  private void handleResetSuccess(String method) {
    new AlertDialog.Builder(LoginActivity.this)
        .setTitle("Password Reset")
        .setMessage("Check your " + method + " for a password reset link.")
        .setNeutralButton("OK", null)
        .show();
  }
}
