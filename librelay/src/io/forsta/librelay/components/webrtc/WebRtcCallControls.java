package io.forsta.librelay.components.webrtc;


import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import io.forsta.librelay.R;
import io.forsta.librelay.components.AccessibleToggleButton;
import io.forsta.librelay.util.ServiceUtil;
import io.forsta.librelay.util.ViewUtil;

public class WebRtcCallControls extends LinearLayout {

  private static final String TAG = WebRtcCallControls.class.getSimpleName();

  private AccessibleToggleButton audioMuteButton;
  private AccessibleToggleButton videoMuteButton;
  private AccessibleToggleButton speakerButton;
  private AccessibleToggleButton bluetoothButton;

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public WebRtcCallControls(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public WebRtcCallControls(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public WebRtcCallControls(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public WebRtcCallControls(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_call_controls, this, true);

    this.speakerButton   = ViewUtil.findById(this, R.id.speakerButton);
    this.bluetoothButton = ViewUtil.findById(this, R.id.bluetoothButton);
    this.audioMuteButton = ViewUtil.findById(this, R.id.muteButton);
    this.videoMuteButton = ViewUtil.findById(this, R.id.video_mute_button);

  }

  public void setAudioMuteButtonListener(final MuteButtonListener listener) {
    audioMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        listener.onToggle(!b);
      }
    });
  }

  public void setVideoMuteButtonListener(final MuteButtonListener listener) {
    videoMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        listener.onToggle(!isChecked);
      }
    });
  }

  public void setSpeakerButtonListener(final SpeakerButtonListener listener) {
    speakerButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        listener.onSpeakerChange(isChecked);
        updateAudioState(bluetoothButton.getVisibility() == View.VISIBLE);
      }
    });
  }

  public void setBluetoothButtonListener(final BluetoothButtonListener listener) {
    bluetoothButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        listener.onBluetoothChange(isChecked);
        updateAudioState(true);
      }
    });
  }

  public void updateAudioState(boolean isBluetoothAvailable) {
    AudioManager audioManager = ServiceUtil.getAudioManager(getContext());

    if (!isBluetoothAvailable) {
      bluetoothButton.setVisibility(View.GONE);
    } else {
      bluetoothButton.setVisibility(View.VISIBLE);
    }
    //Set bluetooth button gone until issues with Bluetooth support are worked out.
    bluetoothButton.setVisibility(View.GONE);

    if (audioManager.isBluetoothScoOn()) {
      bluetoothButton.setChecked(true, false);
      speakerButton.setChecked(false, false);
    } else if (audioManager.isSpeakerphoneOn()) {
      speakerButton.setChecked(true, false);
      bluetoothButton.setChecked(false, false);
    } else {
      speakerButton.setChecked(false, false);
      bluetoothButton.setChecked(false, false);
    }
  }

  public boolean isVideoEnabled() {
    return videoMuteButton.isChecked();
  }

  public void setVideoEnabled(boolean enabled) {
    videoMuteButton.setChecked(enabled, false);
  }

  public void setMicrophoneEnabled(boolean enabled) {
    audioMuteButton.setChecked(enabled, false);
  }

  public void setControlsEnabled(boolean enabled) {
    if (enabled) {
      speakerButton.setAlpha(1.0f);
      bluetoothButton.setAlpha(1.0f);
      videoMuteButton.setAlpha(1.0f);
      audioMuteButton.setAlpha(1.0f);

      speakerButton.setEnabled(true);
      bluetoothButton.setEnabled(true);
      videoMuteButton.setEnabled(true);
      audioMuteButton.setEnabled(true);
    } else {
      speakerButton.setAlpha(0.3f);
      bluetoothButton.setAlpha(0.3f);
      videoMuteButton.setAlpha(0.3f);
      audioMuteButton.setAlpha(0.3f);
      
      speakerButton.setEnabled(false);
      bluetoothButton.setEnabled(false);
      videoMuteButton.setEnabled(false);
      audioMuteButton.setEnabled(false);
    }
  }

  public interface MuteButtonListener {
    public void onToggle(boolean isMuted);
  }

  public interface SpeakerButtonListener {
    public void onSpeakerChange(boolean isSpeaker);
  }

  public interface BluetoothButtonListener {
    public void onBluetoothChange(boolean isBluetooth);
  }
}
