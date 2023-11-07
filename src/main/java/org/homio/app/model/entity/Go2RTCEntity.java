package org.homio.app.model.entity;

import static org.homio.api.util.Constants.DANGER_COLOR;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.api.Context;
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
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.video.Go2RTCService;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Entity
@UISidebarChildren(icon = "fab fa-golang", color = "#3DC4B1", allowCreateItem = false)
public class Go2RTCEntity extends MediaEntity implements HasEntityLog,
    HasGitHubFirmwareVersion, EntityService<Go2RTCService>,
    DeviceEndpointsBehaviourContractStub {

    public static final int RTSP_PORT = 8554;
    public static final GitHubProject go2rtcGitHub =
        GitHubProject.of("AlexxIT", "go2rtc")
                     .setInstalledVersionResolver((context, gitHubProject) -> {
                         Path executable = CommonUtils.getInstallPath().resolve("mediamtx").resolve("mediamtx");
                         return context.hardware().execute(executable + " --version");
                     });

    public static Go2RTCEntity ensureEntityExists(Context context) {
        Go2RTCEntity entity = context.db().getEntity(Go2RTCEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new Go2RTCEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setJsonData("dis_del", true);
            context.db().save(entity);
        }
        return entity;
    }

    @Override
    public String getDescriptionImpl() {
        return "CAMERA.GO2RTC_DESCRIPTION";
    }

    @Override
    public String toString() {
        return "Go2RTC" + getTitle();
    }

    @Override
    public String getDefaultName() {
        return "Go2RTC server";
    }

    @Override
    public void logBuilder(@NotNull EntityLogBuilder builder) {
        builder.addTopic(Go2RTCService.class);
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
        return go2rtcGitHub;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }

    @Override
    public @NotNull Class<Go2RTCService> getEntityServiceItemClass() {
        return Go2RTCService.class;
    }

    @Override
    public Go2RTCService createService(@NotNull Context context) {
        return new Go2RTCService(context, this);
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("api", "rtsp", "webrtc");
    }

    @UIField(order = 30, hideInEdit = true)
    @UIFieldGroup("STATUS")
    public boolean isRunningLocally() {
        return getService().isRunningLocally();
    }

    @UIField(order = 200)
    @UIFieldGroup("CONFIGURATION")
    public int getApiPort() {
        return getJsonData("api", 1984);
    }

    public void setApiPort(int port) {
        setJsonData("api", port);
    }

    @UIField(order = 210)
    @UIFieldGroup("CONFIGURATION")
    public int getRtspPort() {
        return getJsonData("rtsp", 8554);
    }

    public void setRtspPort(int port) {
        setJsonData("rtsp", port);
    }

    @UIField(order = 220)
    @UIFieldGroup("CONFIGURATION")
    public int getWebRtcPort() {
        return getJsonData("webrtc", 8555);
    }

    public void setWebRtcPort(int port) {
        setJsonData("webrtc", port);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "go2rtc";
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

    @SneakyThrows
    @UIContextMenuAction(value = "EDIT_CONFIG",
                         icon = "fas fa-keyboard",
                         iconColor = "#899343")
    public ActionResponseModel editConfig() {
        String content = Files.readString(go2rtcGitHub.getLocalProjectPath().resolve("go2rtc.yaml"));
        return ActionResponseModel.showFile(new FileModel("go2rtc.yaml", content, FileContentType.yaml)
            .setSaveHandler(mc -> getService().updateConfiguration(mc)));
    }

    @SneakyThrows
    @UIContextMenuAction(value = "RESET_CONFIG",
                         confirmMessage = "RESET_CONFIG",
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

    @UIContextMenuAction(value = "GET_LIST",
                         icon = "fab fa-quinscape",
                         iconColor = "#899343")
    public ActionResponseModel apiGetList() {
        return ActionResponseModel.showJson("GET_LIST", getService().getApiList());
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

    public static class StreamEndpoint extends BaseDeviceEndpoint<Go2RTCEntity> {

        private final @Getter String description;
        private final JsonNode node;

        public StreamEndpoint(JsonNode node, Go2RTCEntity entity) {
            super(createIcon(node.get("source").get("type").asText()), "MTX",
                entity.context(), entity, node.get("name").asText(), false, EndpointType.trigger);
            this.node = node;

            boolean ready = node.get("ready").asBoolean();
            if (!ready) {
                getIcon().setColor(Color.RED);
            }
            setValue(new DecimalType(BigDecimal.valueOf(node.get("readers").size()), 0), false);
            this.description = node.get("conf").get("source").asText();
        }

        @Override
        public UIInputBuilder createTriggerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
            uiInputBuilder.addButton(getEntityID(), null, (context, params) ->
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
