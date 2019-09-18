package io.forsta.librelay.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.VideoCapturer;

import static io.forsta.librelay.webrtc.CameraState.Direction.BACK;
import static io.forsta.librelay.webrtc.CameraState.Direction.FRONT;
import static io.forsta.librelay.webrtc.CameraState.Direction.NONE;
import static io.forsta.librelay.webrtc.CameraState.Direction.PENDING;

public class Camera implements CameraVideoCapturer.CameraSwitchHandler {
  private static final String TAG = Camera.class.getSimpleName();
  @Nullable
  private final CameraVideoCapturer   capturer;
  private final CameraEventListener   cameraEventListener;
  private final int                   cameraCount;

  private CameraState.Direction activeDirection;
  private boolean               enabled;

  public Camera(@NonNull Context context, @NonNull CameraEventListener cameraEventListener)
  {
    this.cameraEventListener = cameraEventListener;
    CameraEnumerator enumerator = getCameraEnumerator(context);
    cameraCount = enumerator.getDeviceNames().length;

    CameraVideoCapturer capturerCandidate = createVideoCapturer(enumerator, FRONT);
    if (capturerCandidate != null) {
      activeDirection = FRONT;
    } else {
      capturerCandidate = createVideoCapturer(enumerator, BACK);
      if (capturerCandidate != null) {
        activeDirection = BACK;
      } else {
        activeDirection = NONE;
      }
    }
    capturer = capturerCandidate;
  }

  public void flip() {
    if (capturer == null || cameraCount < 2) {
      Log.w(TAG, "Tried to flip the camera, but we only have " + cameraCount + " of them.");
      return;
    }
    activeDirection = PENDING;
    capturer.switchCamera(this);
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;

    if (capturer == null) {
      return;
    }

    try {
      if (enabled) {
        capturer.startCapture(1280, 720, 30);
      } else {
        capturer.stopCapture();
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Got interrupted while trying to stop video capture", e);
    }
  }

  public void dispose() {
    if (capturer != null) {
      capturer.dispose();
    }
  }

  private int getCount() {
    return cameraCount;
  }

  public @NonNull CameraState.Direction getActiveDirection() {
    return enabled ? activeDirection : NONE;
  }

  public @Nullable CameraVideoCapturer getCapturer() {
    return capturer;
  }

  private @Nullable CameraVideoCapturer createVideoCapturer(@NonNull CameraEnumerator enumerator,
                                                            @NonNull CameraState.Direction direction)
  {
    String[] deviceNames = enumerator.getDeviceNames();
    for (String deviceName : deviceNames) {
      if ((direction == FRONT && enumerator.isFrontFacing(deviceName)) ||
          (direction == BACK  && enumerator.isBackFacing(deviceName)))
      {
        Log.w(TAG, "creating video capturer for: " + deviceName);
        return enumerator.createCapturer(deviceName, null);
      }
    }

    return null;
  }

  private @NonNull CameraEnumerator getCameraEnumerator(@NonNull Context context) {
    boolean camera2EnumeratorIsSupported = false;
    try {
      camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(context);
    } catch (final Throwable throwable) {
      Log.w(TAG, "Camera2Enumator.isSupport() threw.", throwable);
    }

    Log.i(TAG, "Camera2 enumerator supported: " + camera2EnumeratorIsSupported);

    return camera2EnumeratorIsSupported ? new Camera2Enumerator(context)
        : new Camera1Enumerator(true);
  }

  @Override
  public void onCameraSwitchDone(boolean isFrontFacing) {
    activeDirection = isFrontFacing ? FRONT : BACK;
    cameraEventListener.onCameraSwitchCompleted(new CameraState(getActiveDirection(), getCount()));
  }

  @Override
  public void onCameraSwitchError(String errorMessage) {
    Log.e(TAG, "onCameraSwitchError: " + errorMessage);
    cameraEventListener.onCameraSwitchCompleted(new CameraState(getActiveDirection(), getCount()));
  }

  public interface CameraEventListener {
    void onCameraSwitchCompleted(@NonNull CameraState newCameraState);
  }
}
