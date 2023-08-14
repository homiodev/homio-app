package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import de.onvif.soap.OnvifDeviceState;
import de.onvif.soap.devices.InitialDevices;
import jakarta.persistence.Entity;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.EntityContext;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.action.DynamicOptionLoader;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.color.UIFieldColorStatusMatch;
import org.homio.api.ui.field.selection.UIFieldSelection;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.onvif.ver10.device.wsdl.GetDeviceInformationResponse;

@SuppressWarnings("unused")
@Log4j2
@Setter
@Getter
@Entity
public class OnvifCameraEntity extends BaseVideoEntity<OnvifCameraEntity, OnvifCameraService>
    implements HasDynamicContextMenuActions, VideoPlaybackStorage, HasEntityLog {

    @UIField(order = 16)
    @UIFieldSelection(SelectCameraBrand.class)
    public String getCameraType() {
        return getJsonData("cameraType", CameraBrandHandlerDescription.DEFAULT_BRAND.getID());
    }

    public OnvifCameraEntity setCameraType(String cameraType) {
        setJsonData("cameraType", cameraType);
        return this;
    }

    @Override
    public @NotNull String getTitle() {
        return super.getTitle();
    }

    @UIField(order = 1, hideInEdit = true, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
    public String getDescription() {
        if (getIeeeAddress() == null) {
            return "W.ERROR.CAMERA_REQ_AUTH_DESCRIPTION";
        }
        return null;
    }

    @UIField(order = 12, hideInEdit = true, hideOnEmpty = true)
    @UIFieldColorStatusMatch(handlePrefixes = true)
    public String getEventSubscription() {
        if (getStatus().isOnline()) {
            String subscriptionError = getService().getOnvifDeviceState().getSubscriptionError();
            if (subscriptionError != null) {
                return Status.ERROR.name() + " " + subscriptionError;
            }
            return Status.ONLINE.name();
        }
        return Status.UNKNOWN.name();
    }

    @UIField(order = 15, type = UIFieldType.IpAddress)
    public String getIp() {
        return getJsonData("ip");
    }

    public OnvifCameraEntity setIp(String ip) {
        setJsonData("ip", ip);
        return this;
    }

    @UIFieldPort(min = 0)
    @UIField(order = 35)
    public int getOnvifPort() {
        return getJsonData("onvifPort", 8000);
    }

    public OnvifCameraEntity setOnvifPort(int value) {
        setJsonData("onvifPort", value);
        return this;
    }

    @UIFieldPort
    @UIField(order = 36, hideInView = true)
    public int getRestPort() {
        return getJsonData("restPort", 80);
    }

    public void setRestPort(int value) {
        setJsonData("restPort", value);
    }

    @UIField(order = 55, hideInView = true)
    public int getOnvifMediaProfile() {
        return getJsonData("onvifMediaProfile", 0);
    }

    public void setOnvifMediaProfile(int value) {
        setJsonData("onvifMediaProfile", value);
    }

    @Override
    @UIField(order = 45, hideInView = true, label = "cameraUsername")
    public String getUser() {
        return super.getUser();
    }

    @Override
    @UIField(order = 50, hideInView = true, label = "cameraPassword")
    public SecureString getPassword() {
        return super.getPassword();
    }

    @Override
    @UIField(order = 80, hideInView = true)
    public String getAlarmInputUrl() {
        return super.getAlarmInputUrl();
    }

    @UIField(order = 70, hideInView = true)
    public int getNvrChannel() {
        return getJsonData("nvrChannel", 0);
    }

    public void setNvrChannel(int value) {
        setJsonData("nvrChannel", value);
    }

    @Override
    public @NotNull String getSnapshotUrl() {
        return getJsonDataRequire("snapshotUrl", "ffmpeg");
    }

    public void setSnapshotUrl(String value) {
        setJsonData("snapshotUrl", value);
    }

    @UIField(order = 85, hideInView = true)
    public String getCustomMotionAlarmUrl() {
        return getJsonData("customMotionAlarmUrl", "");
    }

    public void setCustomMotionAlarmUrl(String value) {
        setJsonData("customMotionAlarmUrl", value);
    }

    @UIField(order = 90, hideInView = true)
    public String getCustomAudioAlarmUrl() {
        return getJsonData("customAudioAlarmUrl", "");
    }

    public void setCustomAudioAlarmUrl(String value) {
        setJsonData("customAudioAlarmUrl", value);
    }

    @Override
    @UIField(order = 95, hideInView = true)
    public @NotNull String getMjpegUrl() {
        return getJsonDataRequire("mjpegUrl", "ffmpeg");
    }

    @Override
    public @NotNull String getRtspUri() {
        return getFfmpegInput();
    }

    public void setMjpegUrl(String value) {
        setJsonData("mjpegUrl", value);
    }

    @UIField(order = 100, hideInView = true)
    public String getFfmpegInput() {
        return getJsonData("ffmpegInput");
    }

    public void setFfmpegInput(String value) {
        setJsonData("ffmpegInput", value);
    }

    @UIField(order = 105, hideInView = true)
    public String getFfmpegInputOptions() {
        return getJsonData("ffmpegInputOptions", "");
    }

    public void setFfmpegInputOptions(String value) {
        setJsonData("ffmpegInputOptions", value);
    }

    @UIField(order = 155, hideInView = true)
    public boolean isPtzContinuous() {
        return getJsonData("ptzContinuous", false);
    }

    public void setPtzContinuous(boolean value) {
        setJsonData("ptzContinuous", value);
    }

    @Override
    public String getFolderName() {
        return "camera";
    }

    @Override
    public String getDefaultName() {
        return "Onvif camera";
    }

    @Override
    public String toString() {
        return "onvif:" + getIp() + ":" + getOnvifPort();
    }

    @Override
    public @NotNull Icon getEntityIcon() {
        return new Icon("fas fa-video", "#4E783D");
    }

    public long getDeepHashCode() {
        return Objects.hash(getIeeeAddress(), getName()) + getJsonDataHashCode("ip", "cameraType", "onvifPort", "restPort",
            "onvifMediaProfile", "user", "pwd");
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "onvifcam";
    }

    @Override
    @UIFieldIgnore
    public boolean isHasAudioStream() {
        return true;
    }

    public void tryUpdateData(EntityContext entityContext, String ip, Integer port) {
        if (!getIp().equals(ip) || getOnvifPort() != port) {
            if (!getIp().equals(ip)) {
                log.info("[{}]: Onvif camera <{}> changed ip address from <{}> to <{}>", getEntityID(), this, getIp(), ip);
            }
            if (!getIp().equals(ip)) {
                log.info("[{}]: Onvif camera <{}> changed port from <{}> to <{}>", getEntityID(), this, getOnvifPort(), port);
            }
            entityContext.updateDelayed(this, entity -> entity.setIp(ip).setOnvifPort(port));
        }
    }

    @UIContextMenuAction(value = "RESTART", icon = "fas fa-power-off", iconColor = Color.RED)
    public ActionResponseModel reboot() {
        String response = getService().getOnvifDeviceState().getInitialDevices().reboot();
        return ActionResponseModel.showSuccess(response);
    }

    @Override
    public UIInputBuilder assembleActions() {
        UIInputBuilder uiInputBuilder = super.assembleActions();
        if (uiInputBuilder != null) {
            for (UIEntityItemBuilder uiEntity : uiInputBuilder.getUiEntityItemBuilders(true)) {
                if (!"AUTHENTICATE".equals(uiEntity.getEntityID())) {
                    uiEntity.setDisabled(!this.isStart());
                }
            }

            if (StringUtils.isEmpty(getIeeeAddress()) || !getStatus().isOnline()) {
                uiInputBuilder.addOpenDialogSelectableButton("AUTHENTICATE", new Icon("fas fa-sign-in-alt"), 250,
                    (entityContext, params) -> {

                        String user = params.getString("user");
                        String password = params.getString("password");
                        OnvifCameraEntity entity = entityContext.getEntityRequire(getEntityID());
                        OnvifDeviceState onvifDeviceState = new OnvifDeviceState(getEntityID());
                        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(), user, password);
                        try {
                            CommonUtils.ping(entity.getIp(), entity.getRestPort());
                            entity.setUser(user);
                            entity.setPassword(password);

                            entity.setInfo(onvifDeviceState.getInitialDevices());
                            entityContext.save(entity.setStart(true));
                            entityContext.ui()
                                         .sendSuccessMessage("Onvif camera: " + this + " authenticated successfully");
                        } catch (Exception ex) {
                            entityContext.ui().sendWarningMessage(
                                "Onvif camera: " + this + " fault response: " + ex.getMessage());
                        }
                        return null;
                    }).editDialog(dialogBuilder -> {
                    dialogBuilder.setTitle("AUTHENTICATE", new Icon("fas fa-sign-in-alt"));
                    dialogBuilder.addFlex("main", flex -> {
                        flex.addTextInput("user", getUser(), true);
                        flex.addTextInput("password", getPassword().asString(), false);
                    });
                });
            }
        }

        return uiInputBuilder;
    }

    public void setInfo(InitialDevices initialDevices) {
        setName(initialDevices.getName());
        GetDeviceInformationResponse deviceInformation = initialDevices.getDeviceInformation();
        setModel(deviceInformation.getModel());
        setIeeeAddress(defaultIfEmpty(deviceInformation.getSerialNumber(), deviceInformation.getHardwareId()));
        setFirmwareVersion(deviceInformation.getFirmwareVersion());
        setManufacturer(deviceInformation.getManufacturer());
    }

    @Override
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(EntityContext entityContext, String profile, Date from, Date to)
        throws Exception {
        return getService().getVideoPlaybackStorage().getAvailableDaysPlaybacks(entityContext, profile, from, to);
    }

    @Override
    public List<PlaybackFile> getPlaybackFiles(EntityContext entityContext, String profile, Date from, Date to) throws Exception {
        return getService().getVideoPlaybackStorage().getPlaybackFiles(entityContext, profile, from, to);
    }

    @Override
    public DownloadFile downloadPlaybackFile(EntityContext entityContext, String profile, String fileId, Path path)
        throws Exception {
        return getService().getVideoPlaybackStorage().downloadPlaybackFile(entityContext, profile, fileId, path);
    }

    @Override
    public URI getPlaybackVideoURL(EntityContext entityContext, String fileId) throws Exception {
        return getService().getVideoPlaybackStorage().getPlaybackVideoURL(entityContext, fileId);
    }

    @Override
    public PlaybackFile getLastPlaybackFile(EntityContext entityContext, String profile) {
        return getService().getVideoPlaybackStorage().getLastPlaybackFile(entityContext, profile);
    }

    @Override
    public @NotNull Class<OnvifCameraService> getEntityServiceItemClass() {
        return OnvifCameraService.class;
    }

    @Override
    public OnvifCameraService createService(@NotNull EntityContext entityContext) {
        return new OnvifCameraService(entityContext, this);
    }

    @Override
    public void logBuilder(EntityLogBuilder entityLogBuilder) {
        entityLogBuilder.addTopicFilterByEntityID("org.homio.addon.camera");
        entityLogBuilder.addTopicFilterByEntityID("org.homio.api.video");
    }

    public static class SelectCameraBrand implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            return OptionModel.list(OnvifCameraService.getCameraBrands(parameters.getEntityContext()).keySet());
        }
    }

    @Override
    public long getVideoParametersHashCode() {
        return super.getVideoParametersHashCode() +
            getJsonDataHashCode("cameraType", "ip", "onvifPort", "restPort",
                "onvifMediaProfile", "user", "pwd", "alarmInputUrl", "nvrChannel", "snapshotUrl",
                "customMotionAlarmUrl", "customAudioAlarmUrl", "mjpegUrl", "ffmpegInput",
                "ffmpegInputOptions");
    }
}
