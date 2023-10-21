package org.homio.addon.camera.entity;

import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import org.homio.api.Context;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.core.io.Resource;

public interface CameraPlaybackStorage {

    String getTitle();

    LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(Context context, String profile, Date from, Date to)
            throws Exception;

    List<PlaybackFile> getPlaybackFiles(Context context, String profile, Date from, Date to) throws Exception;

    DownloadFile downloadPlaybackFile(Context context, String profile, String fileId, Path path) throws Exception;

    URI getPlaybackVideoURL(Context context, String fileId) throws Exception;

    @Nullable PlaybackFile getLastPlaybackFile(Context context, String profile);

    @AllArgsConstructor
    class PlaybackFile {

        public String id;
        public String name;
        public Date startTime;
        public Date endTime;
        public int size;
        public String type;
    }

    record DownloadFile(Resource stream, long size, String name, JSONObject metadata) {
    }
}
