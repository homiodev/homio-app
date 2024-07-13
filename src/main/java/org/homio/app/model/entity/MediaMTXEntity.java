package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.entity.version.HasGitHubFirmwareVersion;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.FileContentType;
import org.homio.api.model.FileModel;
import org.homio.api.model.Icon;
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
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.video.MediaMTXService;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Entity
@CreateSingleEntity
@UISidebarChildren(icon = "fas fa-square-rss", color = "#308BB3", allowCreateItem = false)
public class MediaMTXEntity extends MediaEntity implements HasEntityLog,
        HasGitHubFirmwareVersion, EntityService<MediaMTXService>,
        DeviceEndpointsBehaviourContractStub {

    public static final GitHubProject mediamtxGitHub =
            GitHubProject.of("bluenviron", "mediamtx")
                    .setInstalledVersionResolver((context, gitHubProject) -> {
                        Path executable = CommonUtils.getInstallPath().resolve("mediamtx").resolve("mediamtx");
                        return context.hardware().execute(executable + " --version");
                    });

    public static MediaMTXEntity getEntity(Context context) {
        return context.db().getEntity(MediaMTXEntity.class, PRIMARY_DEVICE);
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

    @UIField(order = 200)
    public int getApiVersion() {
        return getJsonData("av", 3);
    }

    public void setApiVersion(int value) {
        setJsonData("av", value);
    }

    @Override
    public void logBuilder(@NotNull EntityLogBuilder builder) {
        builder.addTopic(MediaMTXService.class);
    }

    @UIField(order = 1, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", false);
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
    public MediaMTXService createService(@NotNull Context context) {
        return new MediaMTXService(context, this);
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("ll", "rt", "wt", "rbc", "umps", "av", "ap", "rtsp", "webrtc", "hls");
    }

    @UIField(order = 1, hideInEdit = true, color = "#C4CC23")
    @UIFieldGroup("STATUS")
    public int getConnectedStreamsCount() {
        return optService().map(s -> s.getApiList().path("itemCount").asInt(-1)).orElse(-1);
    }

    @UIField(order = 30, hideInEdit = true)
    @UIFieldGroup("STATUS")
    public boolean isRunningLocally() {
        return optService().map(MediaMTXService::isRunningLocally).orElse(false);
    }

    @UIField(order = 200)
    @UIFieldReadDefaultValue
    @UIFieldGroup("CONFIGURATION")
    public int getApiPort() {
        return getJsonData("ap", 9997);
    }

    public void setApiPort(int port) {
        setJsonData("ap", port);
    }

    @UIField(order = 210)
    @UIFieldReadDefaultValue
    @UIFieldGroup("CONFIGURATION")
    public int getRtspPort() {
        return getJsonData("rtsp", 8564);
    }

    public void setRtspPort(int port) {
        setJsonData("rtsp", port);
    }

    @UIField(order = 220)
    @UIFieldReadDefaultValue
    @UIFieldGroup("CONFIGURATION")
    public int getWebRtcPort() {
        return getJsonData("webrtc", 8889);
    }

    public void setWebRtcPort(int port) {
        setJsonData("webrtc", port);
    }

    @UIField(order = 230)
    @UIFieldReadDefaultValue
    @UIFieldGroup("CONFIGURATION")
    public int getHlsPort() {
        return getJsonData("hls", 8888);
    }

    public void setHlsPort(int port) {
        setJsonData("hls", port);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "mediamtx";
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @UIContextMenuAction(value = "GET_LIST",
            icon = "fab fa-quinscape",
            iconColor = "#899343")
    public ActionResponseModel apiGetList() {
        return ActionResponseModel.showJson("GET_LIST", getService().getApiList());
    }

    @SneakyThrows
    @UIContextMenuAction(value = "EDIT_CONFIG",
            icon = "fas fa-keyboard",
            iconColor = "#899343")
    public ActionResponseModel editConfig() {
        String content = Files.readString(getService().getConfigurationPath());
        return ActionResponseModel.showFile(new FileModel("mediamtx.yml", content, FileContentType.yaml)
                .setSaveHandler(mc -> getService().updateConfiguration(mc)));
    }

    @SneakyThrows
    @UIContextMenuAction(value = "RESET_CONFIG",
            confirmMessage = "RESET_CONFIG",
            confirmMessageDialogColor = Color.ERROR_DIALOG,
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

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

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

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public @Nullable String getIeeeAddress() {
        return getEntityID();
    }

    @Override
    @UIFieldIgnore
    public @Nullable String getImageIdentifier() {
        return super.getImageIdentifier();
    }

    public static class StreamEndpoint extends BaseDeviceEndpoint<MediaMTXEntity> {

        private final @Getter String description;
        private final JsonNode node;

        public StreamEndpoint(JsonNode node, MediaMTXEntity entity) {
            super(createIcon(node.get("source").get("type").asText()), "MTX",
                    entity.context(), entity, node.get("name").asText(), false, EndpointType.trigger);
            this.node = node;

            boolean ready = node.get("ready").asBoolean();
            if (!ready) {
                getIcon().setColor(Color.RED);
            }
            setValue(new DecimalType(BigDecimal.valueOf(node.get("readers").size()), 0), false);
            this.description = node.path("conf").path("source").asText();
        }

        @Override
        public UIInputBuilder createTriggerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
            uiInputBuilder.addButton(getEntityID(), null, (context, params) ->
                            ActionResponseModel.showJson("TITLE.NODE_INFO", node))
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
