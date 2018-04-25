package org.thoughtcrime.securesms.webrtc;

import android.support.annotation.NonNull;

public class CameraState {

  public static final CameraState UNKNOWN = new CameraState(Direction.NONE, 0);

  private final int       count;
  private final Direction active;

  public CameraState(@NonNull Direction active, int count) {
    this.active = active;
    this.count  = count;
  }

  public int getCount() {
    return count;
  }

  public Direction getActive() {
    return active;
  }

  public boolean isEnabled() {
    return this.active != Direction.NONE;
  }

  public boolean isAvailable() {
    return count > 0;
  }

  public enum Direction {
    FRONT, BACK, NONE
  }
}
