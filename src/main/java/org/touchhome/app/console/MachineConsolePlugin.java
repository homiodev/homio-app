package org.touchhome.app.console;

import com.fazecast.jSerialComm.SerialPort;
import com.pivovarit.function.ThrowingSupplier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePluginTable;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.util.BoardInfo;
import org.touchhome.bundle.cloud.setting.ConsoleCloudProviderSetting;

@Component
@RequiredArgsConstructor
public class MachineConsolePlugin implements ConsolePluginTable<MachineConsolePlugin.HardwarePluginEntity> {

  @Getter
  private final EntityContext entityContext;

  private final MachineHardwareRepository machineHardwareRepository;
  private final NetworkHardwareRepository networkHardwareRepository;

  @Override
  public String getParentTab() {
    return "hardware";
  }

  @Override
  @SneakyThrows
  public Collection<HardwarePluginEntity> getValue() {
    UserEntity user = entityContext.getUser(false);

    List<HardwarePluginEntity> list = new ArrayList<>();

    list.add(new HardwarePluginEntity("Cpu load", machineHardwareRepository.getCpuLoad()));
    list.add(new HardwarePluginEntity("Cpu temperature", onLinux(machineHardwareRepository::getCpuTemperature)));
    list.add(new HardwarePluginEntity("Ram memory", machineHardwareRepository.getMemory()));
    list.add(new HardwarePluginEntity("SD memory", toString(machineHardwareRepository.getSDCardMemory())));
    list.add(new HardwarePluginEntity("Uptime", machineHardwareRepository.getUptime()));
    String activeNetworkInterface = networkHardwareRepository.getActiveNetworkInterface();
    list.add(new HardwarePluginEntity("Network interface", activeNetworkInterface));
    list.add(new HardwarePluginEntity("Internet stat", toString(networkHardwareRepository.stat(activeNetworkInterface))));
    list.add(new HardwarePluginEntity("Network description",
        toString(networkHardwareRepository.getNetworkDescription(activeNetworkInterface))));
    list.add(new HardwarePluginEntity("Cpu num", Runtime.getRuntime().availableProcessors()));
    list.add(new HardwarePluginEntity("Os", "Name: " + SystemUtils.OS_NAME +
        ". Version: " + SystemUtils.OS_VERSION + ". Arch: " + SystemUtils.OS_ARCH));
    list.add(new HardwarePluginEntity("Java", "Name: " + SystemUtils.JAVA_RUNTIME_NAME + ". Version: " + SystemUtils.JAVA_RUNTIME_VERSION));

    list.add(new HardwarePluginEntity("IP address", networkHardwareRepository.getIPAddress()));
    list.add(new HardwarePluginEntity("Device model",
        EntityContext.isLinuxEnvironment() ? machineHardwareRepository.catDeviceModel() : SystemUtils.OS_NAME));
    list.add(new HardwarePluginEntity("Cloud status",
        this.entityContext.setting().getValue(ConsoleCloudProviderSetting.class).getStatus()));
    list.add(new HardwarePluginEntity("Cloud keystore",
        user.getKeystoreDate() == null ? "" : String.valueOf(user.getKeystoreDate().getTime())));
    list.add(new HardwarePluginEntity("Features", getFeatures()));

    list.add(new HardwarePluginEntity("Board type", BoardInfo.boardType));
    list.add(new HardwarePluginEntity("Processor", BoardInfo.processor));
    list.add(new HardwarePluginEntity("BogoMIPS", BoardInfo.bogoMIPS));
    list.add(new HardwarePluginEntity("Processor features", String.join(";", BoardInfo.features)));
    list.add(new HardwarePluginEntity("Cpu implementor", BoardInfo.cpuImplementer));
    list.add(new HardwarePluginEntity("Cpu architecture", BoardInfo.cpuArchitecture));
    list.add(new HardwarePluginEntity("Cpu variant", BoardInfo.cpuVariant));
    list.add(new HardwarePluginEntity("Cpu part", BoardInfo.cpuPart));
    list.add(new HardwarePluginEntity("Cpu revision", BoardInfo.cpuRevision));
    list.add(new HardwarePluginEntity("Cpu Hardware", BoardInfo.hardware));
    list.add(new HardwarePluginEntity("Cpu Revision", BoardInfo.revision));
    list.add(new HardwarePluginEntity("Cpu Serial", BoardInfo.serial));
    list.add(new HardwarePluginEntity("Hostname", machineHardwareRepository.getHostname()));

    for (SerialPort serialPort : SerialPort.getCommPorts()) {
      list.add(new HardwarePluginEntity("Com port <" + serialPort.getSystemPortName() + ">",
          serialPort.getDescriptivePortName() +
              " [" + serialPort.getBaudRate() + "/" + serialPort.getPortDescription() + "]"));
    }

    Collections.sort(list);

    return list;
  }

  private <T> String toString(T value) {
    return Optional.ofNullable(value).map(Object::toString).orElse("N/A");
  }

  private Object onLinux(ThrowingSupplier<Object, Exception> supplier) throws Exception {
    return EntityContext.isLinuxEnvironment() ? supplier.get() : "N/A";
  }

  private String getFeatures() {
    return entityContext.getDeviceFeatures().entrySet().stream().map(f -> f.getKey() + ": " + f.getValue())
        .collect(Collectors.joining("; "));
  }

  @Override
  public int order() {
    return 1500;
  }

  @Override
  public String getName() {
    return "machine";
  }

  @Override
  public Class<HardwarePluginEntity> getEntityClass() {
    return HardwarePluginEntity.class;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HardwarePluginEntity implements HasEntityIdentifier, Comparable<HardwarePluginEntity> {

    @UIField(order = 1)
    private String name;

    @UIField(order = 2)
    private Object value;

    @Override
    public String getEntityID() {
      return name;
    }

    @Override
    public int compareTo(@NotNull MachineConsolePlugin.HardwarePluginEntity o) {
      return this.name.compareTo(o.name);
    }
  }
}
