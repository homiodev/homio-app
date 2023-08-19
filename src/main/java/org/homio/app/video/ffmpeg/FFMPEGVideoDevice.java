package org.homio.app.video.ffmpeg;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.EntityContextMedia.VideoInputDevice;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Accessors(chain = true)
public class FFMPEGVideoDevice implements VideoInputDevice {

    @Setter
    private String name;
    private final Dimension[] resolutions;
    private Dimension resolution = null;

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

    public Dimension getResolution() {
        if (resolution == null) {
            resolution = getResolutions()[0];
        }
        return resolution;
    }

    public String getResolutionString() {
        Dimension d = getResolution();
        return String.format("%dx%d", d.width, d.height);
    }

    private int arraySize() {
        return resolution.width * resolution.height * 3;
    }

    @Override
    public String toString() {
        return name;
    }
}
