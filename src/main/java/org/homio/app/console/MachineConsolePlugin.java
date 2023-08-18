package org.homio.app.console;

import static java.lang.String.format;

import com.fazecast.jSerialComm.SerialPort;
import com.pivovarit.function.ThrowingSupplier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.homio.hquery.hardware.other.MachineInfo;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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
        List<HardwarePluginEntity> list = new ArrayList<>();

        list.add(new HardwarePluginEntity("Database", format("Type: (%s). Url: (%s)",
                entityContext.setting().getEnv("databaseType"),
                entityContext.setting().getEnv("spring.datasource.url"))));
        list.add(new HardwarePluginEntity("Cpu load", machineHardwareRepository.getCpuLoad()));
        list.add(new HardwarePluginEntity("Cpu temperature", onLinux(machineHardwareRepository::getCpuTemperature)));
        list.add(new HardwarePluginEntity("Ram memory", machineHardwareRepository.getRamMemory()));
        list.add(new HardwarePluginEntity("Disk memory", toString(machineHardwareRepository.getDiscCapacity())));
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
        list.add(new HardwarePluginEntity("Device model", SystemUtils.OS_NAME));

        MachineInfo machineInfo = machineHardwareRepository.getMachineInfo();

        list.add(new HardwarePluginEntity("CPU", "cpus(s) - %s, arch - %s".formatted(machineInfo.getCpuNum(), machineInfo.getArchitecture())));
        list.add(new HardwarePluginEntity("Hostname", machineInfo.getProcessorModelName()));

        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            list.add(new HardwarePluginEntity("Com port <" + serialPort.getSystemPortName() + ">",
                    serialPort.getDescriptivePortName() +
                            " [" + serialPort.getBaudRate() + "/" + serialPort.getPortDescription() + "]"));
        }

        Collections.sort(list);

        return list;
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

    private <T> String toString(T value) {
        return Optional.ofNullable(value).map(Object::toString).orElse("N/A");
    }

    private Object onLinux(ThrowingSupplier<Object, Exception> supplier) throws Exception {
        return SystemUtils.IS_OS_LINUX ? supplier.get() : "N/A";
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
