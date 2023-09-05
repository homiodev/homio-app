package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_MOTION_THRESHOLD;
import static org.homio.api.EntityContextSetting.SERVER_PORT;
import static org.homio.api.model.OptionModel.of;
import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.FileContentType;
import org.homio.api.model.FileModel;
import org.homio.api.model.OptionModel;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.state.State;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIActionButton;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.image.UIFieldImage;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SecureString;
import org.homio.api.workspace.WorkspaceBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@SuppressWarnings("unused")
@Log4j2
public abstract class BaseVideoEntity<T extends BaseVideoEntity, S extends BaseVideoService<?, S>>
    extends MediaEntity implements
    HasEntityLog,
    StreamMJPEG,
    StreamHLS,
    StreamSnapshot,
    DeviceEndpointsBehaviourContract,
    EntityService<S, T> {

    public static final String RUN_CMD = "<span class=\"chip\" style=\"color:%s;border-color: %s;\">%s</span>";

    @UIField(order = 6, hideOnEmpty = true, hideInEdit = true, color = Color.RED)
    @UIFieldGroup("GENERAL")
    public String getPingErrorCount() {
        int count = getService().getCommunicationError();
        return count == 0 ? null : String.valueOf(count);
    }

    @UIField(order = 6, hideInEdit = true, type = UIFieldType.HTML)
    @UIFieldGroup("GENERAL")
    public String getRunningCommands() {
        List<String> commands = new ArrayList<>();
        FFMPEG ffmpegMainReStream = getService().getFfmpegMainReStream();
        if (ffmpegMainReStream != null && ffmpegMainReStream.isRunning()) {
            commands.add(RUN_CMD.formatted("#57A4D1",
                ffmpegMainReStream.getIsAlive() ? "#57A4D1" : "#9C9C9C", ffmpegMainReStream.getDescription()));
        }
        if (FFMPEG.check(getService().getFfmpegSnapshot(), FFMPEG::isRunning, false)) {
            commands.add(RUN_CMD.formatted("#A2D154", "#A2D154", "SNAPSHOT"));
        }
        if (FFMPEG.check(getService().getFfmpegMjpeg(), FFMPEG::isRunning, false)) {
            commands.add(RUN_CMD.formatted("#7FAEAA", "#7FAEAA", "MJPEG"));
        }
        assembleExtraRunningCommands(commands);
        if (commands.isEmpty()) {
            return null;
        }
        return String.join("", commands);
    }

    protected void assembleExtraRunningCommands(List<String> commands) {

    }

    @UIField(order = 1, hideOnEmpty = true, hideInEdit = true)
    @UIFieldGroup(value = "HARDWARE", order = 10, borderColor = Color.RED)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getFirmwareVersion() {
        return getJsonData("fv");
    }

    public void setFirmwareVersion(String value) {
        setJsonData("fv", value);
    }

    @UIField(order = 2, hideOnEmpty = true, hideInEdit = true)
    @UIFieldGroup("HARDWARE")
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getManufacturer() {
        return getJsonData("mf");
    }

    public void setManufacturer(String value) {
        setJsonData("mf", "manufacturer".equalsIgnoreCase(value) ? null : value);
    }

    @UIField(order = 3, hideOnEmpty = true, hideInEdit = true)
    @UIFieldGroup("HARDWARE")
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getSerialNumber() {
        return getJsonData("sn");
    }

    public void setSerialNumber(String value) {
        setJsonData("sn", value);
    }

    @UIField(order = 4, hideOnEmpty = true, hideInEdit = true)
    @UIFieldGroup("HARDWARE")
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getHardwareId() {
        return getJsonData("hid");
    }

    public void setHardwareId(String value) {
        setJsonData("hid", value);
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

    @UIContextMenuAction(value = "RECORD_MP4", icon = "fas fa-file-video", inputs = {
        @UIActionInput(name = "fileName", value = "record_${timestamp}", min = 4, max = 30),
        @UIActionInput(name = "secondsToRecord", type = UIActionInput.Type.number, value = "10", min = 5, max = 100)
    })
    public ActionResponseModel recordMP4(JSONObject params) {
        S service = getService();
        Path filePath = buildFilePathForRecord(service.getFfmpegMP4OutputPath(), params.getString("fileName"), "mp4");
        int secondsToRecord = params.getInt("secondsToRecord");
        log.debug("[{}]: Recording {}.mp4 for {} seconds.", getEntityID(), filePath, secondsToRecord);
        service.recordMp4Async(filePath, null, secondsToRecord);
        return ActionResponseModel.fired();
    }

    @UIField(order = 15, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", false);
    }

    public BaseVideoEntity<?, ?> setStart(boolean start) {
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
        service.recordGifAsync(filePath, null, secondsToRecord);
        return ActionResponseModel.fired();
    }

    @UIField(order = 200, hideInEdit = true)
    @UIFieldCodeEditor(editorType = MonacoLanguage.Json, autoFormat = true)
    public Map<String, State> getAttributes() {
        return optService().map(BaseVideoService::getAttributes).orElse(null);
    }

    public UIInputBuilder assembleActions() {
        return optService().map(BaseVideoService::assembleActions).orElse(null);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @UIField(order = 500, hideInEdit = true)
    @UIFieldImage
    @UIActionButton(name = "refresh", icon = "fas fa-sync",
                    actionHandler = BaseVideoEntity.UpdateSnapshotActionHandler.class)
    @UIActionButton(name = "get", icon = "fas fa-camera",
                    actionHandler = BaseVideoEntity.GetSnapshotActionHandler.class)
    public byte[] getSnapshot() {
        return optService().map(BaseVideoService::getSnapshot).orElse(null);
    }

    // not all entity has username
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

    public int getMotionThreshold() {
        return getJsonData(ENDPOINT_MOTION_THRESHOLD, 40);
    }

    public void setMotionThreshold(int value) {
        setJsonData(ENDPOINT_MOTION_THRESHOLD, value);
    }

    public int getAudioThreshold() {
        return getJsonData(ENDPOINT_AUDIO_THRESHOLD, 40);
    }

    public void setAudioThreshold(int value) {
        setJsonData(ENDPOINT_AUDIO_THRESHOLD, value);
    }

    @UIField(order = 300, hideInView = true)
    @UIFieldGroup("STREAMING")
    public boolean isHasAudioStream() {
        return getJsonData("hasAudioStream", false);
    }

    public void setHasAudioStream(boolean value) {
        setJsonData("hasAudioStream", value);
    }

    @UIField(order = 4, hideOnEmpty = true, type = UIFieldType.Chips, hideInEdit = true)
    @UIFieldGroup("STREAMING")
    public List<String> getStreamResolutions() {
        return getJsonDataList("sr");
    }

    public void setStreamResolutions(String value) {
        setJsonData("sr", value);
    }

    @JsonIgnore
    public List<OptionModel> getVideoSources() {
        List<OptionModel> videoSources = new ArrayList<>();
        assembleStream("Hls streams(.m3u8)", m3u8 -> {
            m3u8.addChild(of("default.m3u8", "HLS stream [default]"));
            if (!getHlsLowResolution().isEmpty()) {
                m3u8.addChild(of("low.m3u8", "HLS stream[%s]".formatted(getHlsLowResolution())));
            }
            if (!getHlsHighResolution().isEmpty()) {
                m3u8.addChild(of("high.m3u8", "HLS stream[%s]".formatted(getHlsHighResolution())));
            }
            appendAdditionHLSStreams(m3u8);
        });
        assembleStream("Mjpeg streams(.jpg)", mjpeg -> {
            mjpeg.addChild(of("ipcamera.mjpeg"));
            //videoSources.add(of("autofps.mjpeg", "MJPEG(autofps) stream"));
            appendAdditionMjpegStreams(mjpeg);
        });
        assembleStream("Mjpeg dash(.mdp)", dash -> {
            //dash.addChild(of("ipcamera.mpd", "MPEG-DASH"));
            appendAdditionMjpegDashStreams(dash);
        });

        return videoSources;
    }

    protected void appendAdditionMjpegDashStreams(OptionModel dash) {

    }

    protected void appendAdditionMjpegStreams(OptionModel mjpeg) {

    }

    protected void appendAdditionHLSStreams(OptionModel m3u8) {

    }

    private OptionModel assembleStream(String name, Consumer<OptionModel> consumer) {
        OptionModel optionModel = of(name, name);
        consumer.accept(optionModel);
        if (optionModel.getChildren() != null && optionModel.getChildren().size() == 1) {
            return optionModel.getChildren().iterator().next();
        } else {
            return optionModel;
        }
    }

    public String getUrl(String path) {
        return "http://%s:%s/rest/media/video/%s/%s".formatted(MACHINE_IP_ADDRESS, SERVER_PORT, getEntityID(), path);
    }

    public @NotNull String getSnapshotUrl() {
        return "ffmpeg";
    }

    public @NotNull String getMjpegUrl() {
        return "ffmpeg";
    }

    public @NotNull
    abstract String getRtspUri();

    protected void fireUpdateSnapshot(EntityContext entityContext, JSONObject params) {
        if (!isStart()) {
            throw new ServerException("ERROR.NOT_STARTED", getTitle());
        }
        getService().scheduleRequestSnapshot();
    }

    @Override
    public void afterDelete() {
        Path path = CommonUtils.getMediaPath().resolve(getFolderName()).resolve(getEntityID());
        if (Files.exists(path)) {
            try {
                FileUtils.deleteDirectory(path.toFile());
            } catch (IOException ex) {
                log.error("Error during delete video directory: {}", path, ex);
            }
        }
    }

    @JsonIgnore
    public @Nullable String getError() {
        if (!isStart()) {
            return "W.ERROR.NOT_STARTED";
        }
        String error = StringUtils.defaultString(getStatusMessage(), "");
        if (containsIgnoreCase(error, "connection timed out")) {
            return "W.ERROR.NOT_REACHED_" + (this.isCamera() ? "CAMERA" : "VIDEO");
        }
        return error;
    }

    @JsonIgnore
    protected boolean isCamera() {
        return true;
    }

    @Override
    @JsonIgnore
    public @Nullable String getStatusMessage() {
        return super.getStatusMessage();
    }

    @JsonIgnore
    public @NotNull String getGroupID() {
        return Objects.requireNonNull(getIeeeAddress());
    }

    public static class UpdateSnapshotActionHandler implements UIActionHandler {

        @Override
        public ActionResponseModel handleAction(EntityContext entityContext, JSONObject params) {
            BaseVideoEntity<?, ?> entity = entityContext.getEntityRequire(params.getString("entityID"));
            entity.fireUpdateSnapshot(entityContext, params);
            return null;
        }
    }

    public static class GetSnapshotActionHandler implements UIActionHandler {

        @Override
        public ActionResponseModel handleAction(EntityContext entityContext, JSONObject params) {
            BaseVideoEntity<?, ?> entity = entityContext.getEntityRequire(params.getString("entityID"));
            byte[] image = entity.getService().recordImageSync(null).byteArrayValue();
            String encodedValue = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image);
            FileModel snapshot = new FileModel("Snapshot", encodedValue, FileContentType.image);
            return ActionResponseModel.showFile(snapshot);
        }
    }

    public long getVideoParametersHashCode() {
        return getJsonDataHashCode("start", "gifOutOptions", "mjpegOutOptions", "imgOutOptions",
            "motionOptions", "mp4OutOptions");
    }

    @Override
    public @NotNull String getDeviceFullName() {
        return "%s(%s) [${%s}]".formatted(
            getTitle(),
            getIeeeAddress(),
            defaultIfEmpty(getPlace(), "W.ERROR.PLACE_NOT_SET"));
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return optService().map(BaseVideoService::getEndpoints).orElse(Map.of());
    }

    @Override
    public @Nullable String getDescriptionImpl() {
        return getError();
    }

    @Override
    public @NotNull ConfigDeviceDefinitionService getConfigDeviceDefinitionService() {
        return BaseVideoService.CONFIG_DEVICE_SERVICE;
    }

    @Override
    public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
        return getService().findDevices();
    }

    @Override
    public void logBuilder(EntityLogBuilder entityLogBuilder) {
        entityLogBuilder.addTopicFilterByEntityID(CameraEntrypoint.class.getPackage());
    }
}
