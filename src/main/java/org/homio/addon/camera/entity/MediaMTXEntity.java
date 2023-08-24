package org.homio.addon.camera.entity;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import java.nio.file.Path;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.service.MediaMTXService;
import org.homio.api.EntityContext;
import org.homio.api.entity.HasFirmwareVersion;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.repository.GitHubProject;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.ApplicationContextHolder;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-square-rss", color = "#308BB3", allowCreateItem = false)
public final class MediaMTXEntity extends MediaEntity implements HasEntityLog,
    HasFirmwareVersion, EntityService<MediaMTXService, MediaMTXEntity> {

    public static final GitHubProject mediamtxGitHub =
        GitHubProject.of("bluenviron", "mediamtx")
                     .setInstalledVersionResolver(() -> {
                         Path executable = CommonUtils.getInstallPath().resolve("mediamtx").resolve("mediamtx");
                         return ApplicationContextHolder.getBean(EntityContext.class)
                                                        .hardware()
                                                        .execute(executable + " --version");
                     });

    public static void ensureEntityExists(EntityContext entityContext) {
        MediaMTXEntity entity = entityContext.getEntity(MediaMTXEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new MediaMTXEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setJsonData("dis_del", true);
            entityContext.save(entity);
        }
        mediamtxGitHub.installLatestRelease(entityContext);
    }

    @Override
    public String getDescriptionImpl() {
        if (!getStatus().isOnline()) {
            String message = StringUtils.defaultString(getStatusMessage(), "");
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
        builder.logToSeparateFile(true);
        builder.addTopicFilterByEntityID(MediaMTXEntity.class);
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
    public String getFirmwareVersion() {
        return mediamtxGitHub.getInstalledVersion();
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
        return getJsonDataHashCode("ll", "rt", "wt", "rbc", "umps");
    }

    @UIField(order = 1)
    @UIFieldSlider(min = 1, max = 60, header = "s")
    @UIFieldGroup(order = 5, value = "CONNECTION", borderColor = "#479923")
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
    @UIFieldSlider(min = 1, max = 60)
    @UIFieldGroup("CONNECTION")
    public LogLevel getLogLevel() {
        return getJsonDataEnum("ll", LogLevel.info);
    }

    public void setLogLevel(LogLevel value) {
        setJsonDataEnum("ll", value);
    }

    @UIField(order = 20, hideInEdit = true)
    @UIFieldGroup("STATUS")
    public String getApiAddress() {
        return getService().getApiURL();
    }

    @UIField(order = 25, hideInEdit = true)
    @UIFieldGroup("STATUS")
    public int getConnectedStreamsCount() {
        return getService().getApiList().path("itemCount").asInt();
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
    public ActionResponseModel scan() {
        return ActionResponseModel.showJson("MEDIAMTX_GET_LIST", getService().getApiList());
    }

    public enum LogLevel {
        error, warn, info, debug
    }
}
