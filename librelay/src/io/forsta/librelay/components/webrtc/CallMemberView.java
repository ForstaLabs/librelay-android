package io.forsta.librelay.components.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.webrtc.SurfaceViewRenderer;

import io.forsta.librelay.R;
import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.util.TextSecurePreferences;
import io.forsta.librelay.util.Util;

public class CallMemberView extends LinearLayout implements Recipient.RecipientModifiedListener {
  private static final String TAG = CallMemberView.class.getSimpleName();

  private Recipient recipient;
  private TextView memberName;
  private TextView callMemberStatus;
  private AvatarImageView memberAvatar;
  public FrameLayout memberVideo;
  private LinearLayout memberInfoContainer;

  public CallMemberView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallMemberView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public CallMemberView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public CallMemberView(@NonNull Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.call_member_view, this, true);

    memberName = (TextView) findViewById(R.id.call_member_name);
    callMemberStatus = (TextView) findViewById(R.id.call_member_status);
    memberAvatar = (AvatarImageView) findViewById(R.id.call_member_avatar);
    memberVideo = (FrameLayout) findViewById(R.id.call_member_video);
    memberInfoContainer = findViewById(R.id.call_member_info_container);
  }

  public void setRecipient(Recipient recipient) {
    this.recipient = recipient;
    this.recipient.addListener(this);
    memberName.setText(recipient.getName());
    memberAvatar.setAvatar(recipient, false);
    if (recipient.getAddress().equals(TextSecurePreferences.getLocalAddress(getContext()))) {
      memberInfoContainer.setVisibility(GONE);
    }
  }

  public void setActiveCall(SurfaceViewRenderer renderer) {
    memberVideo.addView(renderer);
    memberVideo.setVisibility(VISIBLE);
    memberAvatar.setVisibility(GONE);
  }

  public void setCallStatus(String status) {
    callMemberStatus.setText(status);
  }

  @Override
  public void onModified(Recipient recipient) {
    Log.w(TAG, "Recipient modified");
    Util.runOnMain(() -> {
        this.recipient = recipient;
        memberName.setText(recipient.getName());
        memberAvatar.setAvatar(recipient, false);
    });
  }
}
