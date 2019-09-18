package io.forsta.relay;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

public abstract class BaseActionBarActivity extends AppCompatActivity {
  private static final String TAG = BaseActionBarActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    initializeScreenshotSecurity();
  }

  private void initializeScreenshotSecurity() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  protected void startActivitySceneTransition(Intent intent, View sharedView, String transitionName) {
    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, transitionName)
                                         .toBundle();
    ActivityCompat.startActivity(this, intent, bundle);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  protected void setStatusBarColor(int color) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(color);
    }
  }

}
