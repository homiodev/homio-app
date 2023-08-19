package org.homio.addon.camera.entity;

import lombok.AllArgsConstructor;
import org.homio.api.EntityContext;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.core.io.Resource;

import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

public interface VideoPlaybackStorage {

    String getTitle();

    LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(EntityContext entityContext, String profile, Date from, Date to)
            throws Exception;

    List<PlaybackFile> getPlaybackFiles(EntityContext entityContext, String profile, Date from, Date to) throws Exception;

    DownloadFile downloadPlaybackFile(EntityContext entityContext, String profile, String fileId, Path path) throws Exception;

    URI getPlaybackVideoURL(EntityContext entityContext, String fileId) throws Exception;

    @Nullable PlaybackFile getLastPlaybackFile(EntityContext entityContext, String profile);

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
