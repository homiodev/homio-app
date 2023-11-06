package org.homio.addon.firmata.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.Entity;
import javax.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.addon.firmata.arduino.ArduinoConsolePlugin;
import org.homio.addon.firmata.provider.FirmataDeviceCommunicator;
import org.homio.addon.firmata.provider.IODeviceWrapper;
import org.homio.addon.firmata.provider.command.FirmataRegisterCommand;
import org.homio.addon.firmata.provider.command.PendingRegistrationContext;
import org.homio.api.Context;
import org.homio.api.ContextSetting;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.FileContentType;
import org.homio.api.model.FileModel;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.color.UIFieldColorStatusMatch;
import org.homio.api.ui.field.selection.UIFieldSelectConfig;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;


@Entity
@Accessors(chain = true)
public abstract class FirmataBaseEntity<T extends FirmataBaseEntity<T>> extends MicroControllerBaseEntity {

  private static final Map<String, FirmataDeviceCommunicator> entityIDToDeviceCommunicator = new HashMap<>();

  @Setter
  @Getter
  @Transient
  @JsonIgnore
  private FirmataDeviceCommunicator firmataDeviceCommunicator;

  @UIField(order = 11, hideInEdit = true)
  @UIFieldColorStatusMatch
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public Status getJoined() {
    return ContextSetting.getStatus(this, "joined", Status.UNKNOWN);
  }

  public T setJoined(@NotNull Status status) {
    ContextSetting.setStatus(this, "joined", "Joined", status);
    return (T) this;
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @UIField(order = 23, hideInEdit = true)
  public String getBoardType() {
    return getJsonData("boardType");
  }

  public void setBoardType(String boardType) {
    setJsonData("boardType", boardType);
  }

  @Override
  @UIField(order = 100, inlineEdit = true)
  @UIFieldDynamicSelection(SelectTargetFirmataDeviceLoader.class)
  @UIFieldSelectConfig(selectOnEmptyLabel = "selection.target", selectOnEmptyColor = "#A7D21E")
  public String getIeeeAddress() {
    return super.getIeeeAddress();
  }

  @UIContextMenuAction("RESTART")
  public ActionResponseModel restartCommunicator() {
    if (firmataDeviceCommunicator != null) {
      try {
        String response = firmataDeviceCommunicator.restart();
        return ActionResponseModel.showSuccess(response);
      } catch (Exception ex) {
        return ActionResponseModel.showError(ex);
      }
    }
    return ActionResponseModel.showWarn("action.communicator.not_found");
  }

  @UIContextMenuAction("UPLOAD_SKETCH")
  public void uploadSketch(Context context) {

  }

  @UIContextMenuAction("UPLOAD_SKETCH_MANUALLY")
  public void uploadSketchManually(Context context) {
    ArduinoConsolePlugin arduinoConsolePlugin = context.getBean(ArduinoConsolePlugin.class);
    String content = CommonUtils.getResourceAsString("firmata", "arduino_firmata.ino");
    String commName = this.getCommunicatorName();
    String sketch = "#define COMM_" + commName + "\n" + content;
    arduinoConsolePlugin.save(new FileModel("arduino_firmata_" + commName + ".ino", sketch, FileContentType.cpp));
    arduinoConsolePlugin.syncContentToUI();
    context.ui().console().openConsole(arduinoConsolePlugin.getName());
  }

  protected abstract String getCommunicatorName();

  @JsonIgnore
  public short getTarget() {
    return getIeeeAddress() == null ? -1 : Short.parseShort(getIeeeAddress());
  }

  public void setTarget(short target) {
    setIeeeAddress(Short.toString(target));
  }

  @Override
  public String getDefaultName() {
    return "Firmata";
  }

  @Override
  public int getOrder() {
    return 20;
  }

  public abstract FirmataDeviceCommunicator createFirmataDeviceType(Context context);

  @JsonIgnore
  public final IODeviceWrapper getDevice() {
    return firmataDeviceCommunicator.getDevice();
  }

  public long getUniqueID() {
    return getJsonData("uniqueID", 0L);
  }

  public FirmataBaseEntity<T> setUniqueID(Long uniqueID) {
    setJsonData("uniqueID", uniqueID);
    return this;
  }

  protected abstract boolean allowRegistrationType(PendingRegistrationContext pendingRegistrationContext);

  @Override
  public void afterFetch() {
    setFirmataDeviceCommunicator(entityIDToDeviceCommunicator.computeIfAbsent(getEntityID(),
        ignore -> createFirmataDeviceType(context())));
  }

  @Override
  public void beforeDelete() {
    FirmataDeviceCommunicator firmataDeviceCommunicator = entityIDToDeviceCommunicator.remove(getEntityID());
    if (firmataDeviceCommunicator != null) {
      firmataDeviceCommunicator.destroy();
    }
  }

  public static class SelectTargetFirmataDeviceLoader implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
      return parameters.context().getBean(FirmataRegisterCommand.class)
                       .getPendingRegistrations().entrySet().stream()
                       .filter(entry -> ((FirmataBaseEntity) parameters.getBaseEntity()).allowRegistrationType(entry.getValue()))
                       .map(entry -> OptionModel.of(Short.toString(entry.getKey()), entry.getKey() + "/" + entry.getValue()))
                       .collect(Collectors.toList());
    }
  }
}
