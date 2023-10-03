package org.homio.app.model.entity;

import static org.homio.api.util.Constants.DANGER_COLOR;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.entity.version.HasGitHubFirmwareVersion;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.FileContentType;
import org.homio.api.model.FileModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.repository.GitHubProject;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.video.MediaMTXService;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Entity
@UISidebarChildren(icon = "fas fa-square-rss", color = "#308BB3", allowCreateItem = false)
public class MediaMTXEntity extends MediaEntity implements HasEntityLog,
    HasGitHubFirmwareVersion, EntityService<MediaMTXService, MediaMTXEntity>,
    DeviceEndpointsBehaviourContractStub {

    public static final int RTSP_PORT = 8554;
    public static final GitHubProject mediamtxGitHub =
        GitHubProject.of("bluenviron", "mediamtx")
                     .setInstalledVersionResolver((entityContext, gitHubProject) -> {
                         Path executable = CommonUtils.getInstallPath().resolve("mediamtx").resolve("mediamtx");
                         return entityContext.hardware().execute(executable + " --version");
                     });

    public static MediaMTXEntity ensureEntityExists(EntityContext entityContext) {
        MediaMTXEntity entity = entityContext.getEntity(MediaMTXEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new MediaMTXEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setJsonData("dis_del", true);
            entityContext.save(entity);
        }
        return entity;
    }

    @Override
    public String getDescriptionImpl() {
        if (!getStatus().isOnline()) {
            String message = StringUtils.defaultString(getStatusMessage());
            if (!message.isEmpty()) {
                if (message.contains("Access is denied")) {
                    return "W.ERROR.MTX_ACCESS_DENIED";
                }
                return message;
            }
        }
        return "CAMERA.MTX_DESCRIPTION";
    }

    @Override
    public String toString() {
        return "MediaMTX" + getTitle();
    }

    @Override
    public String getDefaultName() {
        return "MediaMTX server";
    }

    @Override
    public void logBuilder(@NotNull EntityLogBuilder builder) {
        builder.addTopic(MediaMTXService.class);
    }

    @UIField(order = 1, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", true);
    }

    public void setStart(boolean start) {
        setJsonData("start", start);
    }

    @Override
    public @NotNull ActionResponseModel update(@NotNull ProgressBar progressBar, @NotNull String version) {
        return getService().updateFirmware(progressBar, version);
    }

    @Override
    public @NotNull GitHubProject getGitHubProject() {
        return mediamtxGitHub;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }

    @Override
    public @NotNull Class<MediaMTXService> getEntityServiceItemClass() {
        return MediaMTXService.class;
    }

    @Override
    public MediaMTXService createService(@NotNull EntityContext entityContext) {
        return new MediaMTXService(entityContext, this);
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("start", "ll", "rt", "wt", "rbc", "umps");
    }

    @UIField(order = 1)
    @UIFieldSlider(min = 1, max = 60, header = "s")
    @UIFieldGroup(order = 50, value = "CONNECTION", borderColor = "#479923")
    public int getReadTimeout() {
        return getJsonData("rt", 10);
    }

    public void setReadTimeout(int value) {
        setJsonData("rt", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 60, header = "s")
    @UIFieldGroup("CONNECTION")
    public int getWriteTimeout() {
        return getJsonData("wt", 10);
    }

    public void setWriteTimeout(int value) {
        setJsonData("wt", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 64, max = 1024, step = 8)
    @UIFieldGroup("CONNECTION")
    public int getReadBufferCount() {
        return getJsonData("rbc", 512);
    }

    public void setReadBufferCount(int value) {
        setJsonData("rbc", value);
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 64, max = 1472)
    @UIFieldGroup("CONNECTION")
    public int getUdpMaxPayloadSize() {
        return getJsonData("umps", 1472);
    }

    public void setUdpMaxPayloadSize(int value) {
        setJsonData("umps", value);
    }

    @UIField(order = 5)
    @UIFieldGroup("CONNECTION")
    public LogLevel getLogLevel() {
        return getJsonDataEnum("ll", LogLevel.info);
    }

    public void setLogLevel(LogLevel value) {
        setJsonDataEnum("ll", value);
    }

    @UIField(order = 1, hideInEdit = true, color = "#C4CC23")
    @UIFieldGroup("STATUS")
    public int getConnectedStreamsCount() {
        return getService().getApiList().path("itemCount").asInt(-1);
    }

    @UIField(order = 30, hideInEdit = true)
    @UIFieldGroup("STATUS")
    public boolean isRunningLocally() {
        return getService().isRunningLocally();
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "mediamtx";
    }

    @Override
    public @NotNull Icon getEntityIcon() {
        return new Icon("fas fa-square-rss", "#308BB3");
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public @Nullable String getPlace() {
        return super.getPlace();
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @UIContextMenuAction(value = "MEDIAMTX_GET_LIST",
                         icon = "fab fa-quinscape",
                         iconColor = "#899343")
    public ActionResponseModel apiGetList() {
        return ActionResponseModel.showJson("MEDIAMTX_GET_LIST", getService().getApiList());
    }

    @SneakyThrows
    @UIContextMenuAction(value = "MEDIAMTX_EDIT_CONFIG",
                         icon = "fas fa-keyboard",
                         iconColor = "#899343")
    public ActionResponseModel editConfig() {
        String content = Files.readString(mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml"));
        return ActionResponseModel.showFile(new FileModel("mediamtx.yml", content, FileContentType.yaml)
            .setSaveHandler(mc -> getService().updateConfiguration(mc)));
    }

    @SneakyThrows
    @UIContextMenuAction(value = "MEDIAMTX_RESET_CONFIG",
                         confirmMessage = "",
                         confirmMessageDialogColor = DANGER_COLOR,
                         icon = "fas fa-clock-rotate-left",
                         iconColor = "#91293E")
    public ActionResponseModel resetConfiguration() {
        getService().resetConfiguration();
        return ActionResponseModel.success();
    }

    @Override
    @SneakyThrows
    public @NotNull ActionResponseModel handleTextFieldAction(
        @NotNull String field,
        @NotNull JSONObject metadata) {
        if (metadata.optString("key", "").equals("config")) {
            return editConfig();
        }
        return ActionResponseModel.showError("W.ERROR.NO_HANDLER");
    }

    public enum LogLevel {
        error, warn, info, debug
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        Map<String, StreamEndpoint> streams = new HashMap<>();
        JsonNode list = getService().getApiList();
        for (JsonNode node : list.path("items")) {
            streams.put(node.toString(), new StreamEndpoint(node, this));
        }
        return streams;
    }

    public static class StreamEndpoint extends BaseDeviceEndpoint<MediaMTXEntity> {

        private final @Getter String description;
        private final JsonNode node;

        public StreamEndpoint(JsonNode node, MediaMTXEntity entity) {
            super(createIcon(node.get("source").get("type").asText()), "MTX",
                entity.getEntityContext(), entity, node.get("name").asText(), false, EndpointType.trigger);
            this.node = node;

            boolean ready = node.get("ready").asBoolean();
            if (!ready) {
                getIcon().setColor(Color.RED);
            }
            setValue(new DecimalType(node.get("readers").size()), false);
            this.description = node.get("conf").get("source").asText();
        }

        @Override
        public UIInputBuilder createTriggerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
            uiInputBuilder.addButton(getEntityID(), null, (entityContext, params) ->
                              ActionResponseModel.showJson("TITLE.MEDIA_MTX_NODE_INFO", node))
                          .setText(getValue().toString());
            return uiInputBuilder;
        }

        private static Icon createIcon(String sourceType) {
            return switch (sourceType) {
                case "srtSource" -> new Icon("fas fa-disease", "#6259B8");
                case "hlsSource" -> new Icon("fas fa-square-rss", "#A62D79");
                case "rpiCameraSource" -> new Icon("fab fa-raspberry-pi", "#B81E00");
                case "rtmpSource" -> new Icon("fas fa-tower-cell", "#9BAA2A");
                case "rtspSource" -> new Icon("fas fa-blog", "#399AAA");
                case "webRTCSource" -> new Icon("fas fa-globe", "#A71ABD");
                case "udpSource" -> new Icon("fas fa-kip-sign", "#3AB2BA");
                default -> new Icon("fas fa-film", "#767873");
            };
        }

        @Override
        public @NotNull String getName(boolean shortFormat) {
            return "/" + getEndpointEntityID();
        }
    }
}
