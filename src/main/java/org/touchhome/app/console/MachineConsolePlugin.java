package org.touchhome.app.console;

import com.fazecast.jSerialComm.SerialPort;
import com.pi4j.system.SystemInfo;
import com.pivovarit.function.ThrowingSupplier;
import lombok.*;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePluginTable;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.cloud.setting.ConsoleCloudProviderSetting;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.entity.UserEntity.ADMIN_USER;

@Component
@RequiredArgsConstructor
public class MachineConsolePlugin implements ConsolePluginTable<MachineConsolePlugin.HardwarePluginEntity> {

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
        UserEntity user = entityContext.getEntity(ADMIN_USER);

        List<HardwarePluginEntity> list = new ArrayList<>();

        list.add(new HardwarePluginEntity("Cpu load", machineHardwareRepository.getCpuLoad()));
        list.add(new HardwarePluginEntity("Cpu temperature", onLinux(SystemInfo::getCpuTemperature)));
        list.add(new HardwarePluginEntity("Ram memory", machineHardwareRepository.getMemory()));
        list.add(new HardwarePluginEntity("SD memory", toString(machineHardwareRepository.getSDCardMemory())));
        list.add(new HardwarePluginEntity("Uptime", machineHardwareRepository.getUptime()));
        String activeNetworkInterface = networkHardwareRepository.getActiveNetworkInterface();
        list.add(new HardwarePluginEntity("Network interface", activeNetworkInterface));
        list.add(new HardwarePluginEntity("Internet stat", toString(networkHardwareRepository.stat(activeNetworkInterface))));
        list.add(new HardwarePluginEntity("Network description", toString(networkHardwareRepository.getNetworkDescription(activeNetworkInterface))));
        list.add(new HardwarePluginEntity("Cpu features", onLinux(SystemInfo::getCpuFeatures)));
        list.add(new HardwarePluginEntity("Cpu num", Runtime.getRuntime().availableProcessors()));
        list.add(new HardwarePluginEntity("Java", SystemUtils.JAVA_RUNTIME_NAME));
        list.add(new HardwarePluginEntity("Os", "Name: " + SystemUtils.OS_NAME +
                ". Version: " + SystemUtils.OS_VERSION + ". Arch: " + SystemUtils.OS_ARCH));

        list.add(new HardwarePluginEntity("IP address", networkHardwareRepository.getIPAddress()));
        list.add(new HardwarePluginEntity("Device model", EntityContext.isLinuxEnvironment() ? machineHardwareRepository.catDeviceModel() : SystemUtils.OS_NAME));
        list.add(new HardwarePluginEntity("Cloud status", this.entityContext.setting().getValue(ConsoleCloudProviderSetting.class).getStatus()));
        list.add(new HardwarePluginEntity("Cloud keystore", user.getKeystoreDate() == null ? "" : String.valueOf(user.getKeystoreDate().getTime())));
        list.add(new HardwarePluginEntity("Features", getFeatures()));

        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            list.add(new HardwarePluginEntity("Com port <" + serialPort.getSystemPortName() + ">", serialPort.getDescriptivePortName() +
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
