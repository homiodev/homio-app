package org.homio.app.video.ffmpeg;

import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class FFMPEGBaseDevice {

  @Setter
  private String name;

  @Override
  public String toString() {
    return name;
  }

  public String getName() {
    return name;
  }
}
