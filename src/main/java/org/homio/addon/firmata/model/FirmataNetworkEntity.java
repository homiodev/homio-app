package org.homio.addon.firmata.model;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.Entity;
import org.apache.commons.lang3.StringUtils;
import org.firmata4j.IODevice;
import org.firmata4j.firmata.FirmataDevice;
import org.firmata4j.transport.NetworkTransport;
import org.homio.api.Context;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.UIField;
import org.homio.addon.firmata.FirmataBundleEntrypoint;
import org.homio.addon.firmata.provider.FirmataDeviceCommunicator;
import org.homio.addon.firmata.provider.command.PendingRegistrationContext;
import org.homio.api.ui.field.selection.UIFieldSelectConfig;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.jetbrains.annotations.NotNull;

@Entity
public final class FirmataNetworkEntity extends FirmataBaseEntity<FirmataNetworkEntity> {

  @UIField(order = 22)
  @Pattern(regexp = "(\\d{1,3}\\.){3}\\d{1,3}", message = "validation.host_port")
  @UIFieldDynamicSelection(value = SelectFirmataIpDeviceLoader.class, rawInput = true)
  @UIFieldSelectConfig(selectOnEmptyLabel = "selection.selectIp",
                       selectOnEmptyColor = "#A7D21E")
  public String getIp() {
    return getJsonData("ip");
  }

  public FirmataNetworkEntity setIp(String ip) {
    setJsonData("ip", ip);

    FirmataBundleEntrypoint.UdpPayload udpPayload = FirmataBundleEntrypoint.getUdpFoundDevices().get(ip);
    if (udpPayload != null) {
      setTarget(udpPayload.getDeviceID());
      setBoardType(udpPayload.getBoard());
    }
    return this;
  }

  @Override
  @UIField(order = 100, hideInEdit = true)
  public String getIeeeAddress() {
    return super.getIeeeAddress();
  }

  @Override
  protected String getCommunicatorName() {
    return "ESP8266_WIFI";
  }

  @Override
  public FirmataDeviceCommunicator createFirmataDeviceType(Context context) {
    return new FirmataNetworkFirmataDeviceCommunicator(context, this);
  }

  @Override
  protected boolean allowRegistrationType(PendingRegistrationContext pendingRegistrationContext) {
    return pendingRegistrationContext.getEntity() instanceof FirmataNetworkEntity;
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return "fmntw";
  }

  private static class FirmataNetworkFirmataDeviceCommunicator extends FirmataDeviceCommunicator<FirmataNetworkEntity> {

    public FirmataNetworkFirmataDeviceCommunicator(Context context, FirmataNetworkEntity entity) {
      super(context, entity);
    }

    @Override
    protected IODevice createIODevice(FirmataNetworkEntity entity) {
      String ip = entity.getIp();
      return StringUtils.isEmpty(ip) ? null : new FirmataDevice(new NetworkTransport(ip + ":3132"));
    }

    @Override
    public long generateUniqueIDOnRegistrationSuccess() {
      return 1;
    }
  }

  public static class SelectFirmataIpDeviceLoader implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
      Map<String, FirmataBundleEntrypoint.UdpPayload> udpFoundDevices = FirmataBundleEntrypoint.getUdpFoundDevices();
      return udpFoundDevices.entrySet().stream().map(e -> OptionModel.of(e.getKey(), e.getValue().toString()))
          .collect(Collectors.toList());
    }
  }
}
