package org.homio.addon.camera.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.onvif.soap.OnvifDeviceState;
import de.onvif.soap.devices.InitialDevices;
import jakarta.persistence.Entity;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.onvif.impl.UnknownBrandHandler;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.Context;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.WebAddress;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UITextInputItemBuilder.InputType;
import org.homio.api.ui.field.color.UIFieldColorStatusMatch;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.onvif.ver10.device.wsdl.GetDeviceInformationResponse;
import org.onvif.ver10.schema.User;
import org.onvif.ver10.schema.UserLevel;

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

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        Set<String> errors = new HashSet<>();
        if (isRequireAuth()) {
            errors.add("W.ERROR.CAMERA_REQ_AUTH_DESCRIPTION");
        }
        return errors;
    }

    @UIField(order = 20)
    @UIFieldDynamicSelection(SelectCameraBrand.class)
    @UIFieldGroup("GENERAL")
    public String getCameraType() {
        return getJsonData("cameraType", UnknownBrandHandler.class.getSimpleName());
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
    public WebAddress getHost() {
        return new WebAddress("%s:%s".formatted(getIp(), getRestPort()), null, new Icon("fas fa-camera", "#9E9035"));
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

    public boolean isSupportPtz() {
        return optService().map(IpCameraService::isSupportPtz).orElse(false);
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

    public void tryUpdateData(Context context, String ip, Integer port) {
        if (!getIp().equals(ip) || getOnvifPort() != port) {
            if (!getIp().equals(ip)) {
                log.info("[{}]: Onvif camera <{}> changed ip address from <{}> to <{}>", getEntityID(), this, getIp(), ip);
            }
            if (getOnvifPort() != port) {
                log.info("[{}]: Onvif camera <{}> changed port from <{}> to <{}>", getEntityID(), this, getOnvifPort(), port);
            }
            context.db().save(setIp(ip).setOnvifPort(port));
        }
    }

    @UIContextMenuAction(value = "RESTART_CAMERA", icon = "fas fa-power-off", iconColor = Color.RED,
                         confirmMessage = "W.CONFIRM.RESTART_CAMERA",
                         confirmMessageDialogColor = Color.ERROR_DIALOG)
    public ActionResponseModel reboot() {
        String response = getService().getOnvifDeviceState().getInitialDevices().reboot();
        return ActionResponseModel.showSuccess(response);
    }

    @Override
    public UIInputBuilder assembleActions() {
        UIInputBuilder uiInputBuilder = super.assembleActions();
        if (uiInputBuilder != null) {
            IpCameraService service = getService();
            if (this.isRequireAuth()) {
                for (UIEntityItemBuilder<?, ?> uiEntity : uiInputBuilder.getUiEntityItemBuilders(true)) {
                    if (!"AUTHENTICATE".equals(uiEntity.getEntityID())) {
                        uiEntity.setDisabled(true);
                    }
                }
                uiInputBuilder.addSelectableButton("AUTHENTICATE", new Icon("fas fa-sign-in-alt", Color.GREEN),
                    (context, params) -> service.authenticate());
            }

            uiInputBuilder.addSelectableButton("GET_INFO", new Icon("fas fa-info"), (context, params) -> {
                JSONObject object = new JSONObject();
                OnvifDeviceState state = service.getOnvifDeviceState();
                object.put("Users", state.getInitialDevices().getUsers());
                object.put("Profiles", state.getProfiles());
                object.put("PTZNode", state.getPtzDevices().getNode(service.getProfile()));
                return ActionResponseModel.showJson("INFO", object);
            });

            uiInputBuilder.addOpenDialogSelectableButton("CREATE_USER", new Icon("fas fa-users", "#009CC4"), null, (context, params) -> {
                String user = params.getString("user");
                String pwd = params.getString("password");
                UserLevel role = UserLevel.valueOf(params.getString("role"));
                if (service.getOnvifDeviceState().getInitialDevices().createUsers(user, role, pwd)) {
                    return ActionResponseModel.success();
                }
                return ActionResponseModel.showError("W.ERROR.UE");
            }).editDialog(builder -> {
                builder.addFlex("main").edit(flex -> {
                    flex.addInput("user", "User name", InputType.Text, true);
                    flex.addInput("password", "Password", InputType.Text, true);
                    List<OptionModel> levels = Stream.of(UserLevel.values())
                                                     .map(l -> OptionModel.of(l.name(), l.value()))
                                                     .collect(Collectors.toList());
                    flex.addSelectBox("role", (context, params) -> null)
                        .setValue(UserLevel.USER.name())
                        .setOptions(levels);
                });
            });

            uiInputBuilder.addSelectableButton("DELETE_USER", new Icon("fas fa-users", "#A02216"), (context, params) -> {
                context.ui().dialog().sendDialogRequest("du", "DELETE_USER", (responseType, pressedButton, parameters) -> {
                    String user = parameters.get("user").asText();
                    if (service.getOnvifDeviceState().getInitialDevices().deleteUser(user)) {
                        context.ui().toastr().success("ACTION.RESPONSE.SUCCESS");
                    }
                    context.ui().toastr().error("W.ERROR.UE");
                }, builder -> {
                    List<User> users = service.getOnvifDeviceState().getInitialDevices().getUsers();
                    if (users.size() < 2) {
                        throw new ServerException("W.ERROR.UNABLE_DELETE_SINGLE_USER");
                    }
                    builder.disableKeepOnUi();
                    builder.appearance(new Icon("fas fa-users"), Color.ERROR_DIALOG);
                    List<ActionInputParameter> inputs = new ArrayList<>();
                    List<OptionModel> userOptions = users.stream().map(u ->
                        OptionModel.of(u.getUsername()).setDescription(u.getUserLevel().toString())).toList();
                    inputs.add(ActionInputParameter.select("user", users.get(0).getUsername(), userOptions));
                    builder.group("General", inputs);
                });
                return null;
            });
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
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(Context context, String profile, Date from, Date to)
            throws Exception {
        return getService().getVideoPlaybackStorage().getAvailableDaysPlaybacks(context, profile, from, to);
    }

    @Override
    public List<PlaybackFile> getPlaybackFiles(Context context, String profile, Date from, Date to) throws Exception {
        return getService().getVideoPlaybackStorage().getPlaybackFiles(context, profile, from, to);
    }

    @Override
    public DownloadFile downloadPlaybackFile(Context context, String profile, String fileId, Path path)
            throws Exception {
        return getService().getVideoPlaybackStorage().downloadPlaybackFile(context, profile, fileId, path);
    }

    @Override
    public URI getPlaybackVideoURL(Context context, String fileId) throws Exception {
        return getService().getVideoPlaybackStorage().getPlaybackVideoURL(context, fileId);
    }

    @Override
    public PlaybackFile getLastPlaybackFile(Context context, String profile) {
        return getService().getVideoPlaybackStorage().getLastPlaybackFile(context, profile);
    }

    @Override
    public @NotNull Class<IpCameraService> getEntityServiceItemClass() {
        return IpCameraService.class;
    }

    @Override
    public IpCameraService createService(@NotNull Context context) {
        return new IpCameraService(context, this);
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
            return OptionModel.list(IpCameraService.getCameraBrands(parameters.context()).keySet());
        }
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("cameraType", "ip", "map",
            "customMotionAlarmUrl", "customAudioAlarmUrl", "nvrChannel",
            "onvifPort", "restPort") + super.getEntityServiceHashCode();
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
