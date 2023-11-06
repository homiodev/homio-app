package org.homio.addon.firmata.model;

import com.fazecast.jSerialComm.SerialPort;
import javax.persistence.Entity;
import org.apache.commons.lang3.StringUtils;
import org.firmata4j.IODevice;
import org.firmata4j.firmata.FirmataDevice;
import org.homio.api.Context;
import org.homio.api.converter.serial.JsonSerialPort;
import org.homio.api.converter.serial.SerialPortDeserializer;
import org.homio.api.optionProvider.SelectSerialPortOptionLoader;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.selection.UIFieldSelectConfig;
import org.homio.addon.firmata.provider.FirmataDeviceCommunicator;
import org.homio.addon.firmata.provider.command.PendingRegistrationContext;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.jetbrains.annotations.NotNull;

@Entity
public final class FirmataUsbEntity extends FirmataBaseEntity<FirmataUsbEntity> {

  @UIField(order = 22)
  @JsonSerialPort
  @UIFieldDynamicSelection(SelectSerialPortOptionLoader.class)
  @UIFieldSelectConfig(selectOnEmptyLabel = "selection.serialPort",
                       selectOnEmptyColor = "#A7D21E")
  public SerialPort getSerialPort() {
    String serialPort = getJsonData("serialPort");
    return SerialPortDeserializer.getSerialPort(serialPort);
  }

  public void setSerialPort(SerialPort serialPort) {
    setJsonData("serialPort", serialPort == null ? "" : serialPort.getSystemPortName());
  }

  @Override
  protected String getCommunicatorName() {
    return "SERIAL";
  }

  @Override
  public FirmataDeviceCommunicator createFirmataDeviceType(Context context) {
    SerialPort serialPort = getSerialPort();
    return serialPort == null ? null : new FirmataUSBFirmataDeviceCommunicator(context, this, serialPort.getSystemPortName());
  }

  @Override
  protected boolean allowRegistrationType(PendingRegistrationContext pendingRegistrationContext) {
    return pendingRegistrationContext.getEntity() instanceof FirmataUsbEntity;
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return "fmusb";
  }

  private static class FirmataUSBFirmataDeviceCommunicator extends FirmataDeviceCommunicator<FirmataUsbEntity> {

    private final String port;

    public FirmataUSBFirmataDeviceCommunicator(Context context, FirmataUsbEntity entity, String port) {
      super(context, entity);
      this.port = port;
    }

    @Override
    protected IODevice createIODevice(FirmataUsbEntity entity) {
      return StringUtils.isEmpty(port) ? null : new FirmataDevice(port);
    }

    @Override
    public long generateUniqueIDOnRegistrationSuccess() {
      return 1;
    }
  }
}
