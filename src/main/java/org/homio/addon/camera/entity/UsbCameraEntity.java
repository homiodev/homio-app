package org.homio.addon.camera.entity;

import static java.lang.String.join;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.service.UsbCameraService;
import org.homio.api.EntityContext;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.action.DynamicOptionLoader;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.homio.api.ui.field.selection.UIFieldSelection;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
@Entity
public final class UsbCameraEntity extends BaseVideoEntity<UsbCameraEntity, UsbCameraService>
        implements AbilityToStreamHLSOverFFMPEG<UsbCameraEntity> {

    @Override
    @UIField(order = 5, label = "usbSource", type = UIFieldType.TextSelectBoxDynamic)
    @UIFieldSelection(SelectVideoSource.class)
    @UIFieldSelectValueOnEmpty(label = "SELECTION.VIDEO_SOURCE")
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    @UIFieldIgnore
    public boolean isHasAudioStream() {
        return super.isHasAudioStream();
    }

    @UIField(order = 25, type = UIFieldType.TextSelectBoxDynamic)
    @UIFieldSelection(SelectAudioSource.class)
    @UIFieldSelectValueOnEmpty(label = "SELECTION.AUDIO_SOURCE")
    public String getAudioSource() {
        return getJsonData("asource");
    }

    public void setAudioSource(String value) {
        setJsonData("asource", value);
    }

    @UIField(order = 100, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup("STREAMING")
    public List<String> getStreamOptions() {
        return getJsonDataList("stream");
    }

    public void setStreamOptions(String value) {
        setJsonData("stream", value);
    }

    @UIField(order = 90, hideInView = true)
    @UIFieldPort
    public int getStreamStartPort() {
        return getJsonData("streamPort", 35001);
    }

    public void setStreamStartPort(int value) {
        setJsonData("streamPort", value);
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

    public long getDeepHashCode() {
        return Objects.hashCode(getIeeeAddress()) + getJsonDataHashCode("start", "asource", "stream", "streamPort");
    }

    @Override
    public String getHlsRtspUri() {
        return "udp://@%s:%s".formatted(CommonUtils.MACHINE_IP_ADDRESS, getStreamStartPort() + 1);
    }

    @Override
    public @NotNull String getRtspUri() {
        return "udp://@%s:%s".formatted(CommonUtils.MACHINE_IP_ADDRESS, getStreamStartPort());
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "usbcam";
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
    protected void beforePersist() {
        super.beforePersist();
        setVideoCodec("libx264");
        setStreamOptions(join("~~~", "-s 800x600"));
    }

    @Override
    public @Nullable String getError() {
        if (StringUtils.isEmpty(getIeeeAddress())) {
            return "W.ERROR.NO_VIDEO_SOURCE_SELECTED";
        }
        return super.getError();
    }

    @Override
    public @NotNull Icon getEntityIcon() {
        return new Icon("fas fa-usb", "#4E783D");
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

    @Override
    public long getVideoParametersHashCode() {
        return super.getVideoParametersHashCode() +
                (getIeeeAddress() == null ? 0 : getIeeeAddress().hashCode()) +
                getJsonDataHashCode("asource", "stream", "streamPort",
                    "extraOpts", "hlsListSize", "vcodec", "acodec", "hls_scale", "sfps", "sbr");
    }
}
