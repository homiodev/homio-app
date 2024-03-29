package org.homio.app.video.ffmpeg;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ContextMedia.VideoInputDevice;
import org.jetbrains.annotations.NotNull;

@Getter
@Accessors(chain = true)
public class FFMPEGVideoDevice implements VideoInputDevice {

    @Setter
    private @NotNull String name;
    private final @NotNull Dimension[] resolutions;

    protected FFMPEGVideoDevice(String resolutions) {
        this.resolutions = readResolutions(resolutions);
    }

    private Dimension[] readResolutions(String res) {
        List<Dimension> resolutions = new ArrayList<>();
        String[] parts = res.split(" ");

        for (String part : parts) {
            String[] xy = part.split("x");
            resolutions.add(new Dimension(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
        }

        return resolutions.toArray(new Dimension[0]);
    }

    @Override
    public String toString() {
        return name;
    }
}
