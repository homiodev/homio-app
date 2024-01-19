package org.homio.app.manager.common.impl;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_MAC;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.service.LocalBoardService.TOTAL_MEMORY;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.util.HardwareUtils;
import org.homio.app.manager.common.ContextImpl;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@RequiredArgsConstructor
public class ContextHardwareImpl implements ContextHardware {

    private final @Getter @Accessors(fluent = true) ContextImpl context;
    private final NetworkHardwareRepository networkHardwareRepository;
    private final MachineHardwareRepository hardwareRepository;

    public ContextHardwareImpl(
        ContextImpl context,
        MachineHardwareRepository machineHardwareRepository,
        NetworkHardwareRepository networkHardwareRepository) {

        this.context = context;
        this.networkHardwareRepository = networkHardwareRepository;
        this.hardwareRepository = machineHardwareRepository;
    }

    public void onContextCreated() throws Exception {
        HardwareUtils.APP_ID = context.setting().getEnv("appId", String.valueOf(System.currentTimeMillis()), true);
        HardwareUtils.MACHINE_IP_ADDRESS = networkHardwareRepository.getIPAddress();
        HardwareUtils.RUN_COUNT = context.setting().getEnv("runCount", 1, true);
        context.setting().setEnv("runCount", HardwareUtils.RUN_COUNT + 1);
    }

    @Override
    public @NotNull String execute(@NotNull String command) {
        return hardwareRepository.execute(command);
    }

    @Override
    public @NotNull String executeNoErrorThrow(@NotNull String command, int maxSecondsTimeout, @Nullable ProgressBar progressBar) {
        return hardwareRepository.executeNoErrorThrow(command, maxSecondsTimeout, progressBar);
    }

    @Override
    public @NotNull ArrayList<String> executeNoErrorThrowList(@NotNull String command, int maxSecondsTimeout,
                                                              @Nullable ProgressBar progressBar) {
        return hardwareRepository.executeNoErrorThrowList(command, maxSecondsTimeout, progressBar);
    }

    @Override
    public @NotNull String execute(@NotNull String command, @Nullable ProgressBar progressBar) {
        return hardwareRepository.execute(command, progressBar);
    }

    @Override
    public @NotNull String execute(@NotNull String command, int maxSecondsTimeout) {
        return hardwareRepository.execute(command, maxSecondsTimeout);
    }

    @Override
    public @NotNull String execute(@NotNull String command, int maxSecondsTimeout, ProgressBar progressBar) {
        return hardwareRepository.execute(command, maxSecondsTimeout, progressBar);
    }

    @Override
    public boolean isSoftwareInstalled(@NotNull String soft) {
        return hardwareRepository.isSoftwareInstalled(soft);
    }

    @Override
    public @NotNull ContextHardware installSoftware(@NotNull String soft, int maxSecondsTimeout) {
        hardwareRepository.installSoftware(soft, maxSecondsTimeout);
        return this;
    }

    @Override
    public @NotNull ContextHardware installSoftware(@NotNull String soft, int maxSecondsTimeout, ProgressBar progressBar) {
        hardwareRepository.installSoftware(soft, maxSecondsTimeout, progressBar);
        return this;
    }

    @Override
    public @NotNull ContextHardware enableSystemCtl(@NotNull String soft) {
        hardwareRepository.enableSystemCtl(soft);
        return this;
    }

    @Override
    public @NotNull ContextHardware startSystemCtl(@NotNull String soft) {
        hardwareRepository.startSystemCtl(soft);
        return this;
    }

    @Override
    public void stopSystemCtl(@NotNull String soft) {
        hardwareRepository.stopSystemCtl(soft);
    }

    @Override
    public int getServiceStatus(@NotNull String serviceName) {
        return hardwareRepository.getServiceStatus(serviceName);
    }

    @Override
    public void reboot() {
        hardwareRepository.reboot();
    }

    @Override
    @SneakyThrows
    public @NotNull ProcessStat getProcessStat(long pid) {
        if (SystemUtils.IS_OS_WINDOWS) {
            String result = execute("powershell -Command \"(Get-Process -Id " + pid + " | Select-Object WS, CPU) | ConvertTo-Json\"");
            JsonNode jsonNode;
            jsonNode = OBJECT_MAPPER.readValue(result, JsonNode.class);
            long ws = jsonNode.path("WS").asLong(0L);
            double cpu = jsonNode.path("CPU").asDouble(0D);
            double pc = 0;
            if (ws > 0) {
                pc = ((double) ws / TOTAL_MEMORY) * 100;
            }
            return new ProcessStatImpl(cpu, pc, ws);
        } else {
            String[] result = execute("ps -p " + pid + " -o %cpu,%mem,rss --no-headers").trim().split(" ");
            return new ProcessStatImpl(
                Double.parseDouble(result[0].trim()),
                Double.parseDouble(result[1].trim()),
                Long.parseLong(result[2].trim()));
        }
    }

    @Override
    public @NotNull JsonNode findAssetByArchitecture(JsonNode release) {
        Architecture architecture = getArchitecture(context);
        Map<String, JsonNode> assetNames = new HashMap<>();
        for (JsonNode asset : release.withArray("assets")) {
            String assetName = asset.get("name").asText();
            if (architecture.matchName.test(assetName)) {
                return asset;
            }
            assetNames.put(assetName, asset);
        }
        if (architecture.name().startsWith("arm")) {
            JsonNode foundAsset = getFoundAsset(assetNames, s -> s.endsWith("_arm"));
            if (foundAsset == null) {
                foundAsset = getFoundAsset(assetNames, s -> s.contains("_arm"));
            }
            if (foundAsset != null) {
                return foundAsset;
            }
        }
        throw new IllegalStateException("Unable to find release asset for current architecture: " + architecture.name()
            + ". Available assets:\n\t" + String.join("\n\t", assetNames.keySet()));
    }

    private static JsonNode getFoundAsset(Map<String, JsonNode> assetNames, Function<String, Boolean> filter) {
        return assetNames.entrySet()
                         .stream()
                         .filter(s -> filter.apply(s.getKey()))
                         .findAny()
                         .map(Entry::getValue)
                         .orElse(null);
    }

    @Getter
    @AllArgsConstructor
    public static class ProcessStatImpl implements ProcessStat {

        private final double cpuUsage;
        private final double memUsage;
        private final long mem;
    }

    private static @NotNull Architecture getArchitecture(@NotNull Context context) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return Architecture.win64;
        }
        String architecture = context.getBean(MachineHardwareRepository.class).getMachineInfo().getArchitecture();
        if (architecture.startsWith("armv6")) {
            return Architecture.arm32v6;
        } else if (architecture.startsWith("armv7")) {
            return Architecture.arm32v7;
        } else if (architecture.startsWith("armv8")) {
            return Architecture.arm32v8;
        } else if (architecture.startsWith("aarch64")) {
            return Architecture.aarch64;
        } else if (architecture.startsWith("x86_64")) {
            return Architecture.amd64;
        }
        throw new IllegalStateException("Unable to find architecture: " + architecture);
    }

    @RequiredArgsConstructor
    public enum Architecture {
        armv6l(s -> s.contains("linux_armv6")),
        armv7l(s -> s.contains("linux_armv7")),
        arm32v6(s -> s.contains("arm32v6") || s.contains("arm6")),
        arm32v7(s -> s.contains("arm32v7") || s.contains("arm7")),
        arm32v8(s -> s.contains("arm32v8") || s.contains("arm8")),
        arm64v8(s -> s.contains("arm64v8")),
        aarch64(s -> IS_OS_LINUX && s.contains("linux_arm64v8") || IS_OS_MAC && s.contains("darwin_arm64")),
        amd64(s -> s.contains("amd64")),
        i386(s -> s.contains("i386")),
        win32(s -> s.contains("win32")),
        win64(s -> s.contains("win64") || s.contains("windows")),
        winArm64(s -> s.contains("win_arm64"));

        public final Predicate<String> matchName;
    }
}
