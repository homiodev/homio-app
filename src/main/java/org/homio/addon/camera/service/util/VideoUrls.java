package org.homio.addon.camera.service.util;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class VideoUrls {

    private @NotNull ProfileUrls defaultProfile = new ProfileUrls();

    private final Map<String, ProfileUrls> urls = new ConcurrentHashMap<>();

    public @NotNull String getMjpegUri() {
        return getOrDefaultUri(profile -> profile.mjpegUri);
    }

    public @NotNull String getRtspUri() {
        return getOrDefaultUri(profile -> profile.rtspUri);
    }

    public @NotNull String getSnapshotUri() {
        return getOrDefaultUri(profile -> profile.snapshotUri);
    }

    public void setMjpegUri(@Nullable String url) {
        defaultProfile.mjpegUri = defaultString(url, "ffmpeg");
    }

    public void setRtspUri(@Nullable String url) {
        defaultProfile.rtspUri = defaultString(url, "ffmpeg");
    }

    public void setSnapshotUri(@Nullable String url) {
        defaultProfile.snapshotUri = defaultString(url, "ffmpeg");
    }

    public @NotNull String getMjpegUri(@Nullable String profile) {
        return getOrDefaultUri(profile, p -> p.mjpegUri);
    }

    public @NotNull String getSnapshotUri(@Nullable String profile) {
        return getOrDefaultUri(profile, p -> p.snapshotUri);
    }

    public @NotNull String getRtspUri(@Nullable String profile) {
        return getOrDefaultUri(profile, p -> p.rtspUri);
    }

    public void setSnapshotUri(@Nullable String url, @Nullable String profile) {
        if (profile == null) {
            setSnapshotUri(url);
        } else {
            urls.computeIfAbsent(profile, s -> new ProfileUrls()).snapshotUri = defaultString(url, defaultProfile.snapshotUri);
        }
    }

    public void setRtspUri(@Nullable String url, @Nullable String profile) {
        if (profile == null) {
            setRtspUri(url);
        } else {
            urls.computeIfAbsent(profile, s -> new ProfileUrls()).rtspUri = defaultString(url, defaultProfile.rtspUri);
        }
    }

    public void clear() {
        this.urls.clear();
    }

    private String getOrDefaultUri(String profile, Function<ProfileUrls, String> uriGetter) {
        String url = null;
        if (profile != null && urls.containsKey(profile)) {
            url = uriGetter.apply(urls.get(profile));
        }
        if (url == null) {
            url = uriGetter.apply(defaultProfile);
        }
        return url;
    }

    private @NotNull String getOrDefaultUri(Function<ProfileUrls, String> uriGetter) {
        String defaultUri = uriGetter.apply(defaultProfile);
        if (defaultUri.equals("ffmpeg") && !urls.isEmpty()) {
            return uriGetter.apply(urls.values().iterator().next());
        }
        return defaultUri;
    }

    @Getter
    private static class ProfileUrls {

        private @NotNull String mjpegUri = "ffmpeg";
        private @NotNull String snapshotUri = "ffmpeg";
        private @NotNull String rtspUri = "ffmpeg";
    }

    @SneakyThrows
    public static String getCorrectUrlFormat(String longUrl) {
        if (longUrl.equals("ffmpeg") || !longUrl.startsWith("http")) {
            return longUrl;
        }

        try {
            URL url = new URL(longUrl);
            int port = url.getPort();
            String temp;
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
        } catch (Exception ignore) {
        }
        return longUrl;
    }
}
