package org.homio.app.console;

import com.fazecast.jSerialComm.SerialPort;
import com.pivovarit.function.ThrowingSupplier;
import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.homio.hquery.hardware.other.MachineInfo;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.lang.String.format;

@Component
@RequiredArgsConstructor
public class MachineConsolePlugin implements ConsolePluginTable<MachineConsolePlugin.HardwarePluginEntity> {

    @Getter
    private final @Accessors(fluent = true) Context context;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final MachineHardwareRepository machineHardwareRepository;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final NetworkHardwareRepository networkHardwareRepository;

    @Override
    public String getParentTab() {
        return "hardware";
    }

    @Override
    public Collection<HardwarePluginEntity> getValue() {
        List<HardwarePluginEntity> list = new ArrayList<>();

        list.add(new HardwarePluginEntity("Database", format("Type: (%s). Url: (%s)",
                context.setting().getEnv("databaseType"),
                context.setting().getEnv("spring.datasource.url"))));
        list.add(new HardwarePluginEntity("Cpu load", machineHardwareRepository.getCpuLoad()));
        list.add(new HardwarePluginEntity("Cpu temperature", onLinux(machineHardwareRepository::getCpuTemperature)));
        list.add(new HardwarePluginEntity("Ram memory", machineHardwareRepository.getRamMemory()));
        list.add(new HardwarePluginEntity("Disk memory", toString(machineHardwareRepository.getDiscCapacity())));
        list.add(new HardwarePluginEntity("Uptime", machineHardwareRepository.getUptime()));
        String activeNetworkInterface = networkHardwareRepository.getActiveNetworkInterface();
        list.add(new HardwarePluginEntity("Network interface", adminOnly(activeNetworkInterface)));
        list.add(new HardwarePluginEntity("Internet stat", adminOnly(toString(networkHardwareRepository.stat(activeNetworkInterface)))));
        list.add(new HardwarePluginEntity("Network description",
                adminOnly(toString(networkHardwareRepository.getNetworkDescription(activeNetworkInterface)))));
        list.add(new HardwarePluginEntity("Cpu num", Runtime.getRuntime().availableProcessors()));
        list.add(new HardwarePluginEntity("Os", "Name: " + SystemUtils.OS_NAME +
                                                ". Version: " + SystemUtils.OS_VERSION + ". Arch: " + SystemUtils.OS_ARCH));
        list.add(new HardwarePluginEntity("Java", "Name: " + SystemUtils.JAVA_RUNTIME_NAME + ". Version: " + SystemUtils.JAVA_RUNTIME_VERSION));

        list.add(new HardwarePluginEntity("IP address", adminOnly(networkHardwareRepository.getIPAddress())));
        list.add(new HardwarePluginEntity("Device model", SystemUtils.OS_NAME));

        MachineInfo machineInfo = machineHardwareRepository.getMachineInfo();

        list.add(new HardwarePluginEntity("CPU", "cpus(s) - %s, arch - %s".formatted(machineInfo.getCpuNum(), machineInfo.getArchitecture())));
        list.add(new HardwarePluginEntity("Hostname", adminOnly(machineInfo.getProcessorModelName())));

        if(context.isAdmin()) {
            for (SerialPort serialPort : SerialPort.getCommPorts()) {
                list.add(new HardwarePluginEntity("Com port <" + serialPort.getSystemPortName() + ">",
                        serialPort.getDescriptivePortName() +
                        " [" + serialPort.getBaudRate() + "/" + serialPort.getPortDescription() + "]"));
            }
        }

        Collections.sort(list);

        return list;
    }

    private Object adminOnly(Object object) {
        return object;
    }

    @Override
    public int order() {
        return 1500;
    }

    @Override
    public @NotNull String getName() {
        return "machine";
    }

    @Override
    public Class<HardwarePluginEntity> getEntityClass() {
        return HardwarePluginEntity.class;
    }

    private <T> String toString(T value) {
        return Optional.ofNullable(value).map(Object::toString).orElse("N/A");
    }

    private Object onLinux(ThrowingSupplier<Object, Exception> supplier) {
        if (SystemUtils.IS_OS_LINUX) {
            try {
                return supplier.get();
            } catch (Exception ignore) {
            }
        }
        return "N/A";
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
