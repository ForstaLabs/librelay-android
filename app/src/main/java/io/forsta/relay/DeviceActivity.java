package io.forsta.relay;

import android.os.Bundle;
import android.view.MenuItem;

import io.forsta.relay.AuthenticationRequiredActionBarActivity;

import io.forsta.librelay.DeviceListFragment;
import io.forsta.librelay.util.DynamicLanguage;

public class DeviceActivity extends AuthenticationRequiredActionBarActivity {
  private static final String TAG = DeviceActivity.class.getSimpleName();

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private DeviceListFragment deviceListFragment;

  @Override
  public void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(io.forsta.librelay.R.string.AndroidManifest__linked_devices);
    this.deviceListFragment = new DeviceListFragment();
    initFragment(android.R.id.content, deviceListFragment, dynamicLanguage.getCurrentLocale());
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }
}
