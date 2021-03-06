package io.forsta.librelay.events;

import android.support.annotation.NonNull;
import android.util.ArrayMap;

import io.forsta.librelay.webrtc.CallRecipient;

import java.util.Map;

public class WebRtcViewModel {

  public enum State {
    // Normal states
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,
    CALL_MEMBER_JOINING,
    CALL_MEMBER_LEAVING,
    CALL_MEMBER_VIDEO,
    VIDEO_ENABLE,
    CALL_ANSWERING,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,
  }


  private final @NonNull  State       state;
  private final CallRecipient callRecipient;
  private String threadUid;
  private int callOrder = 0;
  private final Map<Integer, CallRecipient> remoteCallRecipients; //Remove

  private final boolean localVideoEnabled;
  private final boolean isBluetoothAvailable;
  private final boolean isMicrophoneEnabled;

  public WebRtcViewModel(@NonNull State state, String threadUid, boolean localVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled) {

    this.state = state;
    this.threadUid = threadUid;
    this.callRecipient = null;
    this.remoteCallRecipients = new ArrayMap<>();

    this.localVideoEnabled = localVideoEnabled;
    this.isMicrophoneEnabled = isBluetoothAvailable;
    this.isBluetoothAvailable = isMicrophoneEnabled;
  }

  public WebRtcViewModel(@NonNull State state,
                         Map<Integer, CallRecipient> remoteCallRecipients,
                         @NonNull CallRecipient callRecipient,
                         int callOrder,
                         boolean localVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled)
  {
    this.state                = state;
    this.remoteCallRecipients = remoteCallRecipients;
    this.callRecipient = callRecipient;
    this.callOrder = callOrder;
    this.localVideoEnabled    = localVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public WebRtcViewModel(@NonNull State state, boolean localVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled) {
    this.state = state;
    this.callRecipient = null;
    this.remoteCallRecipients = new ArrayMap<>();
    this.localVideoEnabled    = localVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull Map<Integer, CallRecipient> getRemoteCallRecipients() {
    return remoteCallRecipients;
  }

  public @NonNull
  CallRecipient getCallRecipient() {
    return callRecipient;
  }

  public int getCallOrder() {
    return callOrder;
  }

  public boolean isLocalVideoEnabled() {
    return localVideoEnabled;
  }

  public boolean isBluetoothAvailable() {
    return isBluetoothAvailable;
  }

  public boolean isMicrophoneEnabled() {
    return isMicrophoneEnabled;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[State: ").append(state);

        if (threadUid != null) {
          sb.append(" threadUID: ").append(threadUid);
        }

        if (callRecipient != null) {
          sb.append(" recipient: ").append(callRecipient.getRecipient());
        }
        sb.append(" localVideoEnabled: ").append(localVideoEnabled);
        sb.append("]");
        return sb.toString();
  }
}
