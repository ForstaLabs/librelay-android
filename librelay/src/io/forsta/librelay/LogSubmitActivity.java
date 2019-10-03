package io.forsta.librelay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Created by jlewis on 7/5/17.
 */

public class LogSubmitActivity extends AppCompatActivity implements LogSubmitFragment.OnLogSubmittedListener {

  private static final String TAG = LogSubmitActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.log_submit_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    LogSubmitFragment fragment = LogSubmitFragment.newInstance();
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.replace(R.id.fragment_container, fragment);
    transaction.commit();
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
    }

    return false;
  }

  @Override
  public void onSuccess() {
    Toast.makeText(getApplicationContext(), R.string.log_submit_activity__thanks, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void onFailure() {
    Toast.makeText(getApplicationContext(), R.string.log_submit_activity__log_fetch_failed, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void onCancel() {
    finish();
  }

  @Override
  public void startActivity(Intent intent) {
    try {
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.log_submit_activity__no_browser_installed, Toast.LENGTH_LONG).show();
    }
  }
}
