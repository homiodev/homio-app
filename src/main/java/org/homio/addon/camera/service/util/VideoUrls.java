package org.homio.addon.camera.service.util;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class VideoUrls {

    private @NotNull ProfileUrls defaultProfile = new ProfileUrls();

    // natural ordered to preserve onvif profile orders
    private final Map<String, ProfileUrls> urls = new ConcurrentSkipListMap<>();
    private @Nullable @Setter Function<String, String> uriConverter;

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
        if(profile == null) {
            return getOrDefaultUri(uriGetter);
        }
        String url = null;
        if (urls.containsKey(profile)) {
            url = uriGetter.apply(urls.get(profile));
        }
        if (url == null) {
            url = uriGetter.apply(defaultProfile);
        }
        return url;
    }

    private @NotNull String getOrDefaultUri(Function<ProfileUrls, String> uriGetter) {
        String uri = uriGetter.apply(defaultProfile);
        if (uri.equals("ffmpeg") && !urls.isEmpty()) {
            uri = uriGetter.apply(urls.values().iterator().next());
        }
        return uri.equals("ffmpeg") || uriConverter == null ? uri : uriConverter.apply(uri);
    }

    @Getter
    private static class ProfileUrls {

        private @NotNull String mjpegUri = "ffmpeg";
        private @NotNull String snapshotUri = "ffmpeg";
        private @NotNull String rtspUri = "ffmpeg";
    }
}
