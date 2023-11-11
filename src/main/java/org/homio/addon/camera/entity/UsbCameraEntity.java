package org.homio.addon.camera.entity;

import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.service.UsbCameraService;
import org.homio.api.Context;
import org.homio.api.ContextMedia.VideoInputDevice;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldSelectConfig;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
@Entity
@UISidebarChildren(icon = "fab fa-usb", color = "#AE21B8")
public class UsbCameraEntity extends BaseCameraEntity<UsbCameraEntity, UsbCameraService> {

    @Override
    @UIField(order = 1, label = "usbSource")
    @UIFieldSelectConfig(selectOnEmptyLabel = "SELECTION.VIDEO_SOURCE")
    @UIFieldDynamicSelection(value = SelectVideoSource.class, rawInput = true)
    @UIFieldGroup("GENERAL")
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    public boolean isHasAudioStream() {
        return StringUtils.isNotEmpty(getAudioSource());
    }

    @UIField(order = 2)
    @UIFieldSelectConfig(selectOnEmptyLabel = "SELECTION.AUDIO_SOURCE")
    @UIFieldDynamicSelection(value = SelectAudioSource.class, rawInput = true)
    @UIFieldGroup("GENERAL")
    public String getAudioSource() {
        return getJsonData("asource");
    }

    public void setAudioSource(String value) {
        setJsonData("asource", value);
    }

    @UIField(order = 50)
    @UIFieldBeanSelection(VideoMotionAlarmProvider.class)
    @UIFieldGroup("GENERAL")
    public @Nullable String getVideoMotionAlarmProvider() {
        return getJsonData("map");
    }

    public void setVideoMotionAlarmProvider(String value) {
        setJsonData("map", value);
    }

    @Override
    public String toString() {
        return "usb" + getTitle();
    }

    @Override
    public String getDefaultName() {
        return "Usb camera";
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        if(StringUtils.isEmpty(getIeeeAddress())) {
            return Set.of("W.ERROR.NO_VIDEO_SOURCE_SELECTED");
        }
        return null;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "usbcam";
    }

    @UIField(order = 90, hideInView = true)
    @UIFieldPort
    @UIFieldGroup(value = "STREAMING", order = 10, borderColor = "#59B8AD")
    public int getReStreamUdpPort() {
        return getJsonData("streamPort", 35001);
    }

    public void setReStreamUdpPort(int value) {
        setJsonData("streamPort", value);
    }

    @Override
    @UIFieldDynamicSelection(value = SelectVideoResolutionSource.class, rawInput = true)
    public String getHlsLowResolution() {
        return super.getHlsLowResolution();
    }

    @Override
    @UIFieldDynamicSelection(value = SelectVideoResolutionSource.class, rawInput = true)
    public String getHlsHighResolution() {
        return super.getHlsHighResolution();
    }

    @Override
    public @Nullable String getError() {
        if (StringUtils.isEmpty(getIeeeAddress())) {
            return "W.ERROR.NO_VIDEO_SOURCE_SELECTED";
        }
        return super.getError();
    }

    @Override
    public @Nullable String getModel() {
        return "usb-camera";
    }

    @Override
    public @NotNull Class<UsbCameraService> getEntityServiceItemClass() {
        return UsbCameraService.class;
    }

    @Override
    public UsbCameraService createService(@NotNull Context context) {
        return new UsbCameraService(this, context);
    }

    @Override
    public void beforePersist() {
        super.beforePersist();
        String alarmProvider = context()
            .getBeansOfTypeWithBeanName(VideoMotionAlarmProvider.class)
            .keySet().iterator().next();
        setVideoMotionAlarmProvider(alarmProvider);
    }

    public static class SelectAudioSource implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            return OptionModel.list(parameters.context().media().getAudioDevices());
        }
    }

    public static class SelectVideoSource implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            return OptionModel.list(parameters.context().media().getVideoDevices());
        }
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("asource", "streamPort", "map") + super.getEntityServiceHashCode();
    }

    @UIContextMenuAction(value = "GET_INFO", icon = "fab fa-quinscape", iconColor = "#899343")
    public ActionResponseModel apiGetList() {
        return ActionResponseModel.showJson("GET_INFO", getService().getInput());
    }

    public static class SelectVideoResolutionSource implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            UsbCameraEntity entity = (UsbCameraEntity) parameters.getBaseEntity();
            List<OptionModel> list = new ArrayList<>();
            list.add(OptionModel.of("", "PLACEHOLDER.NOT_SELECTED"));
            if (StringUtils.isNotEmpty(entity.getIeeeAddress())) {
                VideoInputDevice input = parameters.context().media()
                                                   .createVideoInputDevice(entity.getIeeeAddress());
                for (String resolution : entity.getStreamResolutions()) {
                    list.add(OptionModel.of(resolution));
                }
            }
            return list;
        }
    }
}
