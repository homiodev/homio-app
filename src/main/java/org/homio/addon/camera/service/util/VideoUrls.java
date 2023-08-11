package org.homio.addon.camera.service.util;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class VideoUrls {

    private @NotNull ProfileUrls defaultProfile = new ProfileUrls();

    private final Map<String, ProfileUrls> urls = new ConcurrentHashMap<>();

    public String getMjpegUri() {
        return defaultProfile.getMjpegUri();
    }

    public void setMjpegUri(String url) {
        defaultProfile.mjpegUri = url;
    }

    public String getMjpegUri(String profile) {
        return urls.getOrDefault(profile, defaultProfile).mjpegUri;
    }

    public void setMjpegUri(String url, String profile) {
        urls.computeIfAbsent(profile, s -> new ProfileUrls()).mjpegUri = defaultString(url, defaultProfile.mjpegUri);
    }

    public String getSnapshotUri() {
        return defaultProfile.getSnapshotUri();
    }

    public void setSnapshotUri(String url) {
        defaultProfile.snapshotUri = url;
    }

    public String getSnapshotUri(String profile) {
        return urls.getOrDefault(profile, defaultProfile).snapshotUri;
    }

    public void setSnapshotUri(String url, String profile) {
        urls.computeIfAbsent(profile, s -> new ProfileUrls()).snapshotUri = defaultString(url, defaultProfile.snapshotUri);
    }

    public String getRtspUri() {
        return defaultProfile.getRtspUri();
    }

    public void setRtspUri(String url) {
        defaultProfile.rtspUri = url;
    }

    public String getRtspUri(String profile) {
        return urls.getOrDefault(profile, defaultProfile).rtspUri;
    }

    public void setRtspUri(String url, String profile) {
        urls.computeIfAbsent(profile, s -> new ProfileUrls()).rtspUri = defaultString(url, defaultProfile.rtspUri);
    }

    public void clear() {
        this.urls.clear();
    }

    @Getter
    private static class ProfileUrls {

        private @NotNull String mjpegUri = "ffmpeg";
        private @NotNull String snapshotUri = "ffmpeg";
        private @NotNull String rtspUri = "ffmpeg";
    }

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
