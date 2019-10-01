package io.forsta.relay;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import io.forsta.relay.AuthenticationRequiredActionBarActivity;
import io.forsta.relay.ConversationActivity;

import io.forsta.librelay.ConversationListFragment;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.DynamicLanguage;

public class ConversationListArchiveActivity extends AuthenticationRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener
{

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    Bundle bundle = new Bundle();
    bundle.putBoolean(ConversationListFragment.ARCHIVE, true);

    initFragment(android.R.id.content, new ConversationListFragment(),
                 dynamicLanguage.getCurrentLocale(), bundle);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int i = item.getItemId();
    if (i == io.forsta.librelay.R.id.home) {
      super.onBackPressed();
      return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.IS_ARCHIVED_EXTRA, true);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    startActivity(intent);
    overridePendingTransition(io.forsta.librelay.R.anim.slide_from_right, io.forsta.librelay.R.anim.fade_scale_out);
  }

  @Override
  public void onSwitchToArchive() {
    throw new AssertionError();
  }

}
