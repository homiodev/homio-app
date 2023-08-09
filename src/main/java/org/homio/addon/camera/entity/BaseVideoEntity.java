package org.homio.addon.camera.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.api.EntityContext;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.service.EntityService;
import org.homio.api.state.State;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldNumber;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIActionButton;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.image.UIFieldImage;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SecureString;
import org.homio.api.workspace.WorkspaceBlock;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@SuppressWarnings("unused")
@Log4j2
public abstract class BaseVideoEntity<T extends BaseVideoEntity, S extends BaseVideoService<T>>
    extends MediaEntity<T> implements EntityService<S, T> {

    @UIField(order = 300, hideInView = true)
    public boolean isHasAudioStream() {
        return getJsonData("hasAudioStream", false);
    }

    public T setHasAudioStream(boolean value) {
        setJsonData("hasAudioStream", value);
        return (T) this;
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        uiInputBuilder.from(assembleActions());
        uiInputBuilder.fireFetchValues();
    }

    @SneakyThrows
    public static Path buildFilePathForRecord(Path basePath, String fileName, String ext) {
        if (!ext.equals(FilenameUtils.getExtension(fileName))) {
            fileName += "." + ext;
        }
        fileName = basePath.resolve(fileName).toString();
        Path path = Paths.get(WorkspaceBlock.evalStringWithContext(fileName, text -> text));
        Files.createDirectories(path.getParent());
        return path;
    }

    @Override
    public @NotNull S getService() throws NotFoundException {
        return EntityService.super.getService();
    }

    public abstract String getFolderName();

    @UIField(order = 500, hideInEdit = true, type = UIFieldType.Duration)
    public long getLastAnswerFromVideo() {
        return optService().map(BaseVideoService::getLastAnswerFromVideo).orElse(0L);
    }

    @UIContextMenuAction(value = "RECORD_MP4", icon = "fas fa-file-video", inputs = {
        @UIActionInput(name = "fileName", value = "record_${timestamp}", min = 4, max = 30),
        @UIActionInput(name = "secondsToRecord", type = UIActionInput.Type.number, value = "10", min = 5, max = 100)
    })
    public ActionResponseModel recordMP4(JSONObject params) {
        S service = getService();
        Path filePath = buildFilePathForRecord(service.getFfmpegMP4OutputPath(), params.getString("fileName"), "mp4");
        int secondsToRecord = params.getInt("secondsToRecord");
        log.debug("[{}]: Recording {}.mp4 for {} seconds.", getEntityID(), filePath, secondsToRecord);
        service.recordMp4(filePath, null, secondsToRecord);
        return ActionResponseModel.fired();
    }

    @UIField(order = 15, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", false);
    }

    public BaseVideoEntity setStart(boolean start) {
        setJsonData("start", start);
        return this;
    }

    @UIContextMenuAction(value = "RECORD_GIF", icon = "fas fa-magic", inputs = {
        @UIActionInput(name = "fileName", value = "record_${timestamp}", min = 4, max = 30),
        @UIActionInput(name = "secondsToRecord", type = UIActionInput.Type.number, value = "3", min = 1, max = 10)
    })
    public ActionResponseModel recordGif(JSONObject params) {
        S service = getService();
        Path filePath = buildFilePathForRecord(service.getFfmpegGifOutputPath(), params.getString("fileName"), "gif");
        int secondsToRecord = params.getInt("secondsToRecord");
        log.debug("[{}]: Recording {}.gif for {} seconds.", getEntityID(), filePath, secondsToRecord);
        service.recordGif(filePath, null, secondsToRecord);
        return ActionResponseModel.fired();
    }

    @JsonIgnore
    public String getHlsStreamUrl() {
        return "http://" + CommonUtils.MACHINE_IP_ADDRESS + ":" + getService().getServerPort() +
            "/ipvideo.m3u8";
    }

    @UIField(order = 200, hideInEdit = true)
    @UIFieldCodeEditor(editorType = MonacoLanguage.Json, autoFormat = true)
    public Map<String, State> getAttributes() {
        return optService().map(BaseVideoService::getAttributes).orElse(null);
    }

    public UIInputBuilder assembleActions() {
        return optService().map(BaseVideoService::assembleActions).orElse(null);
    }

    @UIField(order = 16, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isAutoStart() {
        return getJsonData("autoStart", true);
    }

    public BaseVideoEntity setAutoStart(boolean start) {
        setJsonData("autoStart", start);
        return this;
    }

    @UIField(order = 250, hideInView = true)
    @UIFieldNumber(min = 1025, max = 65535)
    @UIFieldGroup("GENERAL")
    public Integer getServerPort() {
        return getJsonData("serverPort", 9000);
    }

    public void setServerPort(int value) {
        setJsonData("serverPort", value);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @UIField(order = 500, hideInEdit = true)
    @UIFieldImage
    @UIActionButton(name = "refresh", icon = "fas fa-sync",
                    actionHandler = BaseVideoEntity.UpdateSnapshotActionHandler.class)
    public byte[] getLastSnapshot() {
        return optService().map(BaseVideoService::getLatestSnapshot).orElse(null);
    }

    // not all entity has user name
    public String getUser() {
        return getJsonData("user", "");
    }

    public void setUser(String value) {
        setJsonData("user", value);
    }

    // not all entity has password
    public SecureString getPassword() {
        return getJsonSecure("pwd");
    }

    public void setPassword(String value) {
        setJsonDataSecure("pwd", value);
    }

    public String getAlarmInputUrl() {
        return getJsonData("alarmInputUrl", "");
    }

    public void setAlarmInputUrl(String value) {
        setJsonData("alarmInputUrl", value);
    }

    @Override
    @UIFieldIgnore
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @UIField(order = 125, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup(value = "FFMPEG", order = 70, borderColor = "#3BAD4A")
    public List<String> getGifOutOptions() {
        return getJsonDataList("gifOutOptions");
    }

    public void setGifOutOptions(String value) {
        setJsonData("gifOutOptions", value);
    }

    @UIField(order = 130, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup("FFMPEG")
    public List<String> getMjpegOutOptions() {
        return getJsonDataList("mjpegOutOptions");
    }

    public void setMjpegOutOptions(String value) {
        setJsonData("mjpegOutOptions", value);
    }

    @UIField(order = 135, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup("FFMPEG")
    public List<String> getSnapshotOutOptions() {
        return getJsonDataList("imgOutOptions");
    }

    public void setSnapshotOutOptions(String value) {
        setJsonData("imgOutOptions", value);
    }

    @JsonIgnore
    public String getSnapshotOutOptionsAsString() {
        return String.join(" ", getSnapshotOutOptions());
    }

    @UIField(order = 140, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup("FFMPEG")
    public List<String> getMotionOptions() {
        return getJsonDataList("motionOptions");
    }

    public void setMotionOptions(String value) {
        setJsonData("motionOptions", value);
    }

    @UIField(order = 110)
    @UIFieldSlider(min = 1, max = 100)
    public int getMotionThreshold() {
        return getJsonData("motionThreshold", 40);
    }

    public void setMotionThreshold(int value) {
        setJsonData("motionThreshold", value);
    }

    @UIField(order = 112)
    @UIFieldSlider(min = 1, max = 100)
    public int getAudioThreshold() {
        return getJsonData("audioThreshold", 40);
    }

    public void setAudioThreshold(int value) {
        setJsonData("audioThreshold", value);
    }

    // this is parameter handles when motion/audio detects or when fires snapshot.mjpeg
    @UIField(order = 30)
    @UIFieldSlider(min = 1, max = 30)
    @UIFieldGroup(value = "STREAMING", order = 20, borderColor = "#773FD1")
    public int getSnapshotPollInterval() {
        return getJsonData("spi", 1);
    }

    public void setSnapshotPollInterval(int value) {
        setJsonData("spi", value);
    }

    @UIField(order = 50, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup("STREAMING")
    public List<String> getMp4OutOptions() {
        return getJsonDataList("mp4OutOptions");
    }

    public void setMp4OutOptions(String value) {
        setJsonData("mp4OutOptions", value);
    }

    @JsonIgnore
    public String getSnapshotsMjpegUrl() {
        return "http://" + CommonUtils.MACHINE_IP_ADDRESS + ":" + getService().getServerPort() +
            "/snapshots.mjpeg";
    }

    @JsonIgnore
    public String getAutofpsMjpegUrl() {
        return "http://" + CommonUtils.MACHINE_IP_ADDRESS + ":" + getService().getServerPort() +
            "/autofps.mjpeg";
    }

    @JsonIgnore
    public String getImageUrl() {
        return "http://" + CommonUtils.MACHINE_IP_ADDRESS + ":" + getService().getServerPort() +
            "/ipvideo.jpg";
    }

    @JsonIgnore
    public String getIpVideoMjpeg() {
        return "http://" + CommonUtils.MACHINE_IP_ADDRESS + ":" + getService().getServerPort() +
            "/ipvideo.mjpeg";
    }

    public Set<String> getVideoSources() {
        return Set.of("autofps.mjpeg", "snapshots.mjpeg", "ipvideo.mjpeg", "HLS");
    }

    public String getStreamUrl(String source) {
        return switch (source) {
            case "autofps.mjpeg" -> getAutofpsMjpegUrl();
            case "snapshots.mjpeg" -> getSnapshotsMjpegUrl();
            case "HLS" -> getHlsStreamUrl();
            case "ipvideo.mjpeg" -> getIpVideoMjpeg();
            case "image.jpg" -> getImageUrl();
            default -> null;
        };
    }

    protected void fireUpdateSnapshot(EntityContext entityContext, JSONObject params) {
        if (!isStart()) {
            throw new ServerException("ERROR.NOT_STARTED", getTitle());
        }
        optService().ifPresent(BaseVideoService::startSnapshot);
    }

    @Override
    protected void beforePersist() {
        setMp4OutOptions("-c:v copy~~~-c:a copy");
        setMjpegOutOptions("-q:v 5~~~-r 2~~~-vf scale=640:-2~~~-update 1");
        setSnapshotOutOptions("-vsync vfr~~~-q:v 2~~~-update 1~~~-frames:v 1");
        setGifOutOptions(
            "-r 2~~~-filter_complex scale=-2:360:flags=lanczos,setpts=0.5*PTS,split[o1][o2];[o1]palettegen[p];[o2]fifo[o3];" +
                "[o3][p]paletteuse");
        setServerPort(BaseVideoService.findFreeBootstrapServerPort());
    }

    public static class UpdateSnapshotActionHandler implements UIActionHandler {

        @Override
        public ActionResponseModel handleAction(EntityContext entityContext, JSONObject params) {
            BaseVideoEntity entity = entityContext.getEntityRequire(params.getString("entityID"));
            entity.fireUpdateSnapshot(entityContext, params);
            return null;
        }
    }

    public long getVideoParametersHashCode() {
        return getJsonDataHashCode("serverPort", "gifOutOptions", "mjpegOutOptions", "imgOutOptions",
            "motionOptions", "mp4OutOptions");
    }
}
