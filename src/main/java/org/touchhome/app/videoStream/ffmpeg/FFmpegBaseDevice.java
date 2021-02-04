package org.touchhome.app.videoStream.ffmpeg;

import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class FFmpegBaseDevice {

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
