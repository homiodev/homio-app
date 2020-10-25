package org.touchhome.bundle.machine;

import com.fazecast.jSerialComm.SerialPort;
import com.pi4j.system.SystemInfo;
import com.pivovarit.function.ThrowingSupplier;
import lombok.*;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.cloud.setting.CloudProviderSetting;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;

@Component
@RequiredArgsConstructor
public class MachineConsolePlugin implements ConsolePlugin {

    @Override
    public String getParentTab() {
        return "hardware";
    }

    private final EntityContext entityContext;
    private final LinuxHardwareRepository linuxHardwareRepository;
    private final WirelessHardwareRepository wirelessHardwareRepository;

    @Override
    @SneakyThrows
    public List<? extends HasEntityIdentifier> drawEntity() {
        UserEntity user = entityContext.getEntity(ADMIN_USER);

        List<HardwarePluginEntity> list = new ArrayList<>();

        list.add(new HardwarePluginEntity("Cpu load", linuxHardwareRepository.getCpuLoad()));
        list.add(new HardwarePluginEntity("Cpu temperature", onLinux(SystemInfo::getCpuTemperature)));
        list.add(new HardwarePluginEntity("Ram memory", linuxHardwareRepository.getMemory()));
        list.add(new HardwarePluginEntity("SD memory", toString(linuxHardwareRepository.getSDCardMemory())));
        list.add(new HardwarePluginEntity("Uptime", linuxHardwareRepository.getUptime()));
        String activeNetworkInterface = wirelessHardwareRepository.getActiveNetworkInterface();
        list.add(new HardwarePluginEntity("Network interface", activeNetworkInterface));
        list.add(new HardwarePluginEntity("Internet stat", toString(wirelessHardwareRepository.stat(activeNetworkInterface))));
        list.add(new HardwarePluginEntity("Network description", toString(wirelessHardwareRepository.getNetworkDescription(activeNetworkInterface))));
        list.add(new HardwarePluginEntity("Cpu features", onLinux(SystemInfo::getCpuFeatures)));
        list.add(new HardwarePluginEntity("Java", SystemUtils.JAVA_RUNTIME_NAME));
        list.add(new HardwarePluginEntity("Os", "Name: " + SystemUtils.OS_NAME +
                ". Version: " + SystemUtils.OS_VERSION + ". Arch: " + SystemUtils.OS_ARCH));

        list.add(new HardwarePluginEntity("IP address", wirelessHardwareRepository.getIPAddress()));
        list.add(new HardwarePluginEntity("Router IP address", wirelessHardwareRepository.getGatewayIpAddress()));
        list.add(new HardwarePluginEntity("Device model", EntityContext.isLinuxEnvironment() ? linuxHardwareRepository.catDeviceModel() : SystemUtils.OS_NAME));
        list.add(new HardwarePluginEntity("Cloud status", this.entityContext.getSettingValue(CloudProviderSetting.class).getStatus()));
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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class HardwarePluginEntity implements HasEntityIdentifier, Comparable<HardwarePluginEntity> {
        @UIField(order = 1)
        private String name;

        @UIField(order = 2)
        private Object value;

        @Override
        public String getEntityID() {
            return name;
        }

        @Override
        public Integer getId() {
            return null;
        }

        @Override
        public int compareTo(@NotNull MachineConsolePlugin.HardwarePluginEntity o) {
            return this.name.compareTo(o.name);
        }
    }
}
