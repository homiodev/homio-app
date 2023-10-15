package org.homio.addon.camera.entity;

import static org.homio.api.util.Constants.DANGER_COLOR;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.EntityContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.color.UIFieldColorStatusMatch;
import org.homio.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.onvif.ver10.device.wsdl.GetDeviceInformationResponse;

@SuppressWarnings("unused")
@Log4j2
@Setter
@Getter
@Entity
@UISidebarChildren(icon = "fas fa-clapperboard", color = "#22A896")
public class IpCameraEntity extends BaseCameraEntity<IpCameraEntity, IpCameraService>
    implements
    HasDynamicContextMenuActions,
    CameraPlaybackStorage,
    HasDynamicUIFields {

    @UIField(order = 20)
    @UIFieldDynamicSelection(SelectCameraBrand.class)
    @UIFieldGroup("GENERAL")
    public String getCameraType() {
        return getJsonData("cameraType", CameraBrandHandlerDescription.DEFAULT_BRAND.getID());
    }

    public IpCameraEntity setCameraType(String cameraType) {
        setJsonData("cameraType", cameraType);
        return this;
    }

    @Override
    public @NotNull String getTitle() {
        return super.getTitle();
    }

    @Override
    public String getDescriptionImpl() {
        return getError();
    }

    @UIField(order = 2, hideInEdit = true, hideOnEmpty = true)
    @UIFieldColorStatusMatch(handlePrefixes = true)
    @UIFieldGroup("STATUS")
    public String getEventSubscription() {
        if (getStatus().isOnline()) {
            return optService().map(service -> {
                String subscriptionError = service.getOnvifDeviceState().getSubscriptionError();
                if (subscriptionError != null) {
                    return Status.ERROR.name() + " " + subscriptionError;
                }
                return Status.ONLINE.name();
            }).orElse(Status.UNKNOWN.name());
        }
        return Status.UNKNOWN.name();
    }

    @UIField(order = 1, type = UIFieldType.IpAddress, hideInView = true)
    @UIFieldGroup(order = 1, value = "CONNECTION", borderColor = "#9CA611")
    public String getIp() {
        return getJsonData("ip");
    }

    public IpCameraEntity setIp(String ip) {
        setJsonData("ip", ip);
        return this;
    }

    @UIField(order = 1, hideInEdit = true)
    @UIFieldGroup("CONNECTION")
    public String getHost() {
        return "%s:%s".formatted(getIp(), getRestPort());
    }

    @UIFieldPort
    @UIField(order = 2, hideInView = true)
    @UIFieldGroup("CONNECTION")
    public int getRestPort() {
        return getJsonData("restPort", 80);
    }

    public void setRestPort(int value) {
        setJsonData("restPort", value);
    }

    @UIField(order = 3, hideInView = true)
    @UIFieldGroup("CONNECTION")
    public int getNvrChannel() {
        return getJsonData("nvrChannel", 0);
    }

    public void setNvrChannel(int value) {
        setJsonData("nvrChannel", value);
    }

    @UIField(order = 1, hideInView = true)
    @UIFieldPort(min = 0)
    @UIFieldGroup(order = 30, value = "ONVIF", borderColor = "#314682")
    public int getOnvifPort() {
        return getJsonData("onvifPort", 8000);
    }

    public IpCameraEntity setOnvifPort(int value) {
        setJsonData("onvifPort", value);
        return this;
    }

    @Override
    @UIField(order = 1, hideInView = true, label = "cameraUsername")
    @UIFieldGroup(order = 7, value = "SECURITY", borderColor = "#23ADAB")
    public String getUser() {
        return super.getUser();
    }

    @Override
    @UIField(order = 2, hideInView = true, label = "cameraPassword")
    @UIFieldGroup("SECURITY")
    public SecureString getPassword() {
        return super.getPassword();
    }

    @UIField(order = 2, hideInView = true)
    @UIFieldGroup("ADVANCED")
    public String getCustomMotionAlarmUrl() {
        return getJsonData("customMotionAlarmUrl", "");
    }

    public void setCustomMotionAlarmUrl(String value) {
        setJsonData("customMotionAlarmUrl", value);
    }

    @UIField(order = 3, hideInView = true)
    @UIFieldGroup("ADVANCED")
    public String getCustomAudioAlarmUrl() {
        return getJsonData("customAudioAlarmUrl", "");
    }

    public void setCustomAudioAlarmUrl(String value) {
        setJsonData("customAudioAlarmUrl", value);
    }

    @Override
    public @Nullable String getError() {
        if (isRequireAuth()) {
            return "W.ERROR.CAMERA_REQ_AUTH_DESCRIPTION";
        }
        return super.getError();
    }

    @Override
    public void assembleUIFields(@NotNull UIFieldBuilder uiFieldBuilder) {
        getService().getBrandHandler().assembleUIFields(uiFieldBuilder);
    }

    @JsonIgnore
    private boolean isRequireAuth() {
        return getIeeeAddress() == null;
    }

    @UIField(order = 155, hideInView = true)
    @UIFieldGroup("ONVIF")
    @UIFieldDisableEditOnCondition("return !context.get('supportPtz')")
    public boolean isPtzContinuous() {
        return getJsonData("ptzContinuous", false);
    }

    public boolean isSupportPtz() {
        return optService().map(IpCameraService::isSupportPtz).orElse(false);
    }

    public void setPtzContinuous(boolean value) {
        setJsonData("ptzContinuous", value);
    }

    @Override
    public String getDefaultName() {
        return "Ip camera";
    }

    @Override
    public String toString() {
        return "ipcam:%s:%d".formatted(getIp(), getOnvifPort());
    }

    @Override
    public @NotNull Icon getEntityIcon() {
        return new Icon("fas fa-video", "#4E783D");
    }

    @Override
    public long getEntityServiceHashCode() {
        return Objects.hash(getIeeeAddress(), getName()) + getJsonDataHashCode("start", "ip", "cameraType", "onvifPort", "restPort",
                "onvifMediaProfile", "user", "pwd");
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ipcam";
    }

    @Override
    public boolean isHasAudioStream() {
        return true;
    }

    @Override
    public @Nullable String getVideoMotionAlarmProvider() {
        return null;
    }

    public void tryUpdateData(EntityContext entityContext, String ip, Integer port) {
        if (!getIp().equals(ip) || getOnvifPort() != port) {
            if (!getIp().equals(ip)) {
                log.info("[{}]: Onvif camera <{}> changed ip address from <{}> to <{}>", getEntityID(), this, getIp(), ip);
            }
            if (getOnvifPort() != port) {
                log.info("[{}]: Onvif camera <{}> changed port from <{}> to <{}>", getEntityID(), this, getOnvifPort(), port);
            }
            entityContext.save(setIp(ip).setOnvifPort(port));
        }
    }

    @UIContextMenuAction(value = "RESTART_CAMERA", icon = "fas fa-power-off", iconColor = Color.RED,
                         confirmMessage = "W.CONFIRM.RESTART_CAMERA",
                         confirmMessageDialogColor = DANGER_COLOR)
    public ActionResponseModel reboot() {
        String response = getService().getOnvifDeviceState().getInitialDevices().reboot();
        return ActionResponseModel.showSuccess(response);
    }

    @Override
    public UIInputBuilder assembleActions() {
        UIInputBuilder uiInputBuilder = super.assembleActions();
        if (uiInputBuilder != null) {
            if (this.isRequireAuth()) {
                for (UIEntityItemBuilder<?, ?> uiEntity : uiInputBuilder.getUiEntityItemBuilders(true)) {
                    if (!"AUTHENTICATE".equals(uiEntity.getEntityID())) {
                        uiEntity.setDisabled(true);
                    }
                }
                uiInputBuilder.addSelectableButton("AUTHENTICATE", new Icon("fas fa-sign-in-alt", Color.GREEN),
                        (entityContext, params) -> getService().authenticate());
            }
        }

        return uiInputBuilder;
    }

    public void setInfo(OnvifDeviceState onvifDeviceState, boolean requireAuth) {
        setIp(onvifDeviceState.getIp());
        setOnvifPort(onvifDeviceState.getOnvifPort());
        setUser(onvifDeviceState.getUsername());
        setPassword(onvifDeviceState.getPassword());
        if (!requireAuth) {
            setIeeeAddress(onvifDeviceState.getIEEEAddress(false));
            InitialDevices initialDevices = onvifDeviceState.getInitialDevices();
            setName(initialDevices.getName());
            GetDeviceInformationResponse deviceInformation = initialDevices.getDeviceInformation();
            setModel(deviceInformation.getModel());
            setFirmwareVersion(deviceInformation.getFirmwareVersion());
            setManufacturer(deviceInformation.getManufacturer());
            setSerialNumber(deviceInformation.getSerialNumber());
            setHardwareId(deviceInformation.getHardwareId());

            setStart(true);
        }
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
    public @NotNull Class<IpCameraService> getEntityServiceItemClass() {
        return IpCameraService.class;
    }

    @Override
    public IpCameraService createService(@NotNull EntityContext entityContext) {
        return new IpCameraService(entityContext, this);
    }

    public void setActiveLink(String value) {
        setJsonData("al", value);
    }

    @UIField(order = 4, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("CONNECTION")
    public String getActiveLink() {
        return getJsonData("al");
    }

    public void setMac(String value) {
        setJsonData("mac", value);
    }

    @UIField(order = 5, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("CONNECTION")
    public String getMac() {
        return getJsonData("mac");
    }

    public static class SelectCameraBrand implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            return OptionModel.list(IpCameraService.getCameraBrands(parameters.getEntityContext()).keySet());
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

    @Override
    public @Nullable ActionResponseModel handleTextFieldAction(
            @NotNull String field,
            @NotNull JSONObject metadata) {
        if (metadata.optString("key", "").equals("auth")) {
            return getService().authenticate();
        }
        return ActionResponseModel.showError("W.ERROR.NO_HANDLER");
    }
}