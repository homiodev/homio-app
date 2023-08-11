package org.homio.addon.camera.service.util;

import java.net.URL;
import lombok.SneakyThrows;

public final class VideoUtils {

    @SneakyThrows
    public static String getCorrectUrlFormat(String longUrl) {
        String temp;

        if (longUrl.isEmpty() || longUrl.equals("ffmpeg")) {
            return longUrl;
        }

        URL url = new URL(longUrl);
        int port = url.getPort();
        if (port == -1) {
            if (url.getQuery() == null) {
                temp = url.getPath();
            } else {
                temp = url.getPath() + "?" + url.getQuery();
            }
        } else {
            if (url.getQuery() == null) {
                temp = ":" + url.getPort() + url.getPath();
            } else {
                temp = ":" + url.getPort() + url.getPath() + "?" + url.getQuery();
            }
        }
        return temp;
    }
}
