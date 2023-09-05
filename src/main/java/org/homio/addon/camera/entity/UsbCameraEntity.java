package org.homio.addon.camera.entity;

import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.service.UsbCameraService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.VideoInputDevice;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.selection.UIFieldSelectConfig;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.app.model.entity.MediaMTXEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
@Entity
public class UsbCameraEntity extends BaseVideoEntity<UsbCameraEntity, UsbCameraService> {

    @Override
    @UIField(order = 1, label = "usbSource")
    @UIFieldSelectConfig(selectOnEmptyLabel = "SELECTION.VIDEO_SOURCE")
    @UIFieldDynamicSelection(value = SelectVideoSource.class, rawInput = true)
    @UIFieldGroup("GENERAL")
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    @UIFieldIgnore
    public boolean isHasAudioStream() {
        return super.isHasAudioStream();
    }

    @Override
    protected void appendAdditionHLSStreams(OptionModel m3u8) {
        m3u8.addChild(OptionModel.of("mediamtx.m3u8", "MediaMTX HLS"));
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

    @Override
    protected void assembleExtraRunningCommands(List<String> commands) {
        if (FFMPEG.check(getService().getFfmpegUsbStream(), FFMPEG::isRunning, false)) {
            commands.add(RUN_CMD.formatted("#D05362", "#D05362", "TEE"));
        }
    }

    @Override
    public String getFolderName() {
        return "camera";
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
    public long getEntityServiceHashCode() {
        return Objects.hashCode(getIeeeAddress()) + getJsonDataHashCode("start", "asource", "stream", "streamPort");
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

    @UIField(order = 100, hideInView = true)
    @UIFieldGroup("STREAMING")
    @UIFieldSlider(min = 1, max = 60)
    public int getStreamFramesPerSecond() {
        return getJsonData("sfps", 15);
    }

    public void setStreamFramesPerSecond(int value) {
        setJsonData("sfps", value);
    }

    @UIField(order = 100, hideInView = true)
    @UIFieldGroup("STREAMING")
    @UIFieldSlider(min = 100, max = 2500)
    public int getStreamBitRate() {
        return getJsonData("sbr", 500);
    }

    public void getStreamBitRate(int value) {
        setJsonData("sbr", value);
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
    public UsbCameraService createService(@NotNull EntityContext entityContext) {
        return new UsbCameraService(this, entityContext);
    }

    public static class SelectAudioSource implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            return OptionModel.list(parameters.getEntityContext().media().getAudioDevices());
        }
    }

    public static class SelectVideoSource implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            return OptionModel.list(parameters.getEntityContext().media().getVideoDevices());
        }
    }

    public static class SelectVideoResolutionSource implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            UsbCameraEntity entity = (UsbCameraEntity) parameters.getBaseEntity();
            List<OptionModel> list = new ArrayList<>();
            list.add(OptionModel.of("", "PLACEHOLDER.NOT_SELECTED"));
            if (StringUtils.isNotEmpty(entity.getIeeeAddress())) {
                VideoInputDevice input = parameters.getEntityContext().media()
                                                   .createVideoInputDevice(entity.getIeeeAddress());
                for (String resolution : entity.getStreamResolutions()) {
                    list.add(OptionModel.of(resolution));
                }
            }
            return list;
        }
    }

    @Override
    public long getVideoParametersHashCode() {
        return super.getVideoParametersHashCode() +
                (getIeeeAddress() == null ? 0 : getIeeeAddress().hashCode()) +
            getJsonDataHashCode("asource", "stream", "streamPort", "sfps", "sbr");
    }

    @UIContextMenuAction(value = "GET_INFO", icon = "fab fa-quinscape", iconColor = "#899343")
    public ActionResponseModel apiGetList() {
        return ActionResponseModel.showJson("GET_INFO", getService().getInput());
    }
}
