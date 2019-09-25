package io.forsta.relay;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import io.forsta.librelay.DashboardActivity;

import io.forsta.librelay.atlas.AtlasPreferences;
import io.forsta.librelay.atlas.model.AtlasUser;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    TextView header = findViewById(R.id.app_header);
    TextView text = findViewById(R.id.app_text);
    TextView loginInfo = findViewById(R.id.app_atlas_login_info);
    Button loginButton = findViewById(R.id.app_relay_login);
    Button dashboardButton = findViewById(R.id.app_relay_dashboard);
    dashboardButton.setOnClickListener(view -> {
      Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
      startActivity(intent);
    });

    loginButton.setOnClickListener(view -> {
      Intent intent = new Intent(MainActivity.this, ConversationListActivity.class);
      startActivity(intent);
    });

    boolean registered = AtlasPreferences.isRegisteredAtlas(this);

    if (registered) {
      text.setText("This device is registered");
      AtlasUser user = AtlasUser.getLocalUser(this);
      loginInfo.setText(user.getName());
      loginInfo.append("\n@" + user.getFullTag());
      dashboardButton.setVisibility(View.VISIBLE);
    } else {
      text.setText("This device is NOT registered.");
      loginInfo.setVisibility(View.GONE);
      dashboardButton.setVisibility(View.GONE);
    }
  }
}
