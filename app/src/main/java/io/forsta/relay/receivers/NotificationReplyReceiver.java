package io.forsta.relay.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.forsta.librelay.notifications.ReplyReceiver;
import io.forsta.relay.ConversationActivity;
import io.forsta.relay.ConversationListActivity;

public class NotificationReplyReceiver extends ReplyReceiver {
  private static final String TAG = NotificationReplyReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    long threadId = intent.getLongExtra(ReplyReceiver.THREAD_ID, -1);
    Log.d(TAG, "notification reply intent received for threadId: " + threadId);
    Intent replyIntent = new Intent();
    if (threadId == -1) {
      replyIntent.setClassName(context.getPackageName(), ConversationListActivity.class.getName());
    } else {
      replyIntent.setClassName(context.getPackageName(), ConversationActivity.class.getName());
      replyIntent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    }
    replyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(replyIntent);
  }
}
