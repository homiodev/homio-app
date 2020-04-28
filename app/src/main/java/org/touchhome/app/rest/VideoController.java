package org.touchhome.app.rest;

import lombok.AllArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.ImageManager;
import org.touchhome.app.manager.VideoManager;

import java.io.IOException;

@RestController
@RequestMapping("/rest/video")
@AllArgsConstructor
public class VideoController {

    private final VideoManager videoManager;
    private final ImageManager imageManager;

    @GetMapping("isCameraDetected")
    public Boolean isCameraDetected() {
        return videoManager.isCameraDetected();
    }

    @GetMapping("snapshot")
    public ResponseEntity<InputStreamResource> takeSnapshot(@RequestParam(value = "width", defaultValue = "640") Integer width,
                                                            @RequestParam(value = "height", defaultValue = "480") Integer height) throws IOException {
        return imageManager.getImage(videoManager.takeSnapshot(width, height));
    }

    @GetMapping("stream")
    public ResponseEntity<InputStreamResource> startVideo(@RequestParam(value = "width", defaultValue = "1024") Integer width,
                                                          @RequestParam(value = "height", defaultValue = "768") Integer height,
                                                          @RequestParam(value = "saveToFile", defaultValue = "true") Boolean saveToFile) {
        videoManager.startVideo(width, height, saveToFile);
        return null;
    }

    @GetMapping("isStream")
    public Boolean isStreamVideo() {
        return videoManager.isStreamVideo();
    }

    @GetMapping("stop")
    public void stopVideo() {
        videoManager.stopVideo();
    }

    @GetMapping("name")
    public String getCameraName() {
        return videoManager.getCameraName();
    }
}
