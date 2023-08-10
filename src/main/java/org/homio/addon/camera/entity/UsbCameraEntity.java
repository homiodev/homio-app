package org.homio.addon.camera.entity;

import static java.lang.String.join;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.homio.addon.camera.service.UsbCameraService;
import org.homio.api.EntityContext;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.action.DynamicOptionLoader;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.homio.api.ui.field.selection.UIFieldSelection;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
@Entity
public final class UsbCameraEntity extends BaseVideoEntity<UsbCameraEntity, UsbCameraService>
    implements AbilityToStreamHLSOverFFMPEG<UsbCameraEntity>, HasEntityLog {

    @Override
    @UIField(order = 5, label = "usb")
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
  @UIFieldSelectValueOnEmpty(label = "selection.audioSource")
  public String getAudioSource() {
    return getJsonData("asource");
  }

  public void setAudioSource(String value) {
    setJsonData("asource", value);
  }

  @UIField(order = 100, hideInView = true, type = UIFieldType.Chips)
  public List<String> getStreamOptions() {
    return getJsonDataList("stream");
  }

  public void setStreamOptions(String value) {
    setJsonData("stream", value);
  }

  @UIField(order = 90, hideInView = true)
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
    return Objects.hashCode(getIeeeAddress()) + getJsonDataHashCode("asource", "stream", "streamPort");
  }

    @Override
  protected @NotNull String getDevicePrefix() {
    return "usbcam";
  }

  @Override
  protected void beforePersist() {
    super.beforePersist();
    setVideoCodec("libx264");
    setSnapshotOutOptions(join("~~~", "-vsync vfr", "-q:v 2", "-update 1", "-frames:v 10"));
    setStreamOptions(join("~~~",
        "-vcodec libx264", "-s 800x600", "-bufsize:v 5M", "-preset ultrafast", "-vcodec libx264", "-tune zerolatency", "-b:v " +
            "2.5M"));
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

  @Override
  public void logBuilder(EntityLogBuilder entityLogBuilder) {
      entityLogBuilder.addTopicFilterByEntityID("org.homio.addon.camera");
      entityLogBuilder.addTopicFilterByEntityID("org.homio.api.video");
  }

  public static class SelectAudioSource implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
      Set<String> audioDevices = parameters.getEntityContext().media().getAudioDevices();
      return OptionModel.list(audioDevices);
    }
  }

  @Override
  public long getVideoParametersHashCode() {
    return super.getVideoParametersHashCode() +
        (getIeeeAddress() == null ? 0 : getIeeeAddress().hashCode()) +
        getJsonDataHashCode("asource", "stream", "streamPort",
            "extraOpts", "hlsListSize", "vcodec", "acodec", "hls_scale");
  }
}
