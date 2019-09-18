// vim: ts=2:sw=2:expandtab
package io.forsta.relay;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import io.forsta.relay.BaseActionBarActivity;
import io.forsta.relay.RegistrationProgressActivity;

import io.forsta.librelay.util.Dialogs;

/* XXX Deprecated activity in Atlas onboarding model.
 * Just forwards to the progress activity now. */
public class RegistrationActivity extends BaseActionBarActivity {

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
    if (result != ConnectionResult.SUCCESS) {
      Dialogs.showAlertDialog(this, getString(io.forsta.librelay.R.string.RegistrationActivity_unsupported),
          getString(io.forsta.librelay.R.string.RegistrationActivity_sorry_this_device_is_not_supported_for_data_messaging));
      return;
    }
    Intent intent = new Intent(this, RegistrationProgressActivity.class);
    startActivity(intent);
    finish();
  }
}
