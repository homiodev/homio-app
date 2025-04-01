package org.homio.app.manager.common.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.ContextNetwork;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.HardwareUtils;
import org.homio.app.console.MachineConsolePlugin;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.service.cloud.CloudService;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;

import java.net.ConnectException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.service.device.LocalBoardService.TOTAL_MEMORY;

@Log4j2
@RequiredArgsConstructor
public class ContextHardwareImpl implements ContextHardware {

  private final @Getter
  @Accessors(fluent = true) ContextImpl context;
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

  private static JsonNode getFoundAsset(Map<String, JsonNode> assetNames, Function<String, Boolean> filter) {
    return assetNames.entrySet()
      .stream()
      .filter(s -> filter.apply(s.getKey()))
      .findAny()
      .map(Entry::getValue)
      .orElse(null);
  }

  public static @NotNull Architecture getArchitecture(@NotNull Context context) {
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

  public void onContextCreated() throws Exception {
    String uuid = CommonUtils.generateShortUUID(8);
    HardwareUtils.APP_ID = context.setting().getEnv("appId", uuid, true);
    MACHINE_IP_ADDRESS = networkHardwareRepository.getIPAddress();
    HardwareUtils.RUN_COUNT = context.setting().getEnv("runCount", 1, true);
    context.setting().setEnv("runCount", HardwareUtils.RUN_COUNT + 1);
  }

  @Override
  public @Nullable String execute(@NotNull String command) {
    return hardwareRepository.execute(command);
  }

  @Override
  public @NotNull String executeNoErrorThrow(@NotNull String command, int maxSecondsTimeout, @Nullable ProgressBar progressBar) {
    return hardwareRepository.executeNoErrorThrow(command, maxSecondsTimeout, progressBar);
  }

  @Override
  public @NotNull List<String> executeNoErrorThrowList(@NotNull String command, int maxSecondsTimeout,
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
  public @NotNull ContextHardware uninstallSoftware(@NotNull String soft, int maxSecondsTimeout, @Nullable ProgressBar progressBar) {
    hardwareRepository.uninstallSoftware(soft, maxSecondsTimeout, progressBar);
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
  public @NotNull ContextHardware addHardwareInfo(@NotNull String name, @NotNull String value) {
    context.getBean(MachineConsolePlugin.class).addHardwareInfo(name, value);
    return this;
  }

  @Override
  public @NotNull JsonNode findAssetByArchitecture(JsonNode release) {
    Architecture architecture = getArchitecture(context);
    Map<String, JsonNode> assetNames = new HashMap<>();
    for (JsonNode asset : release.withArray("assets")) {
      String assetName = asset.get("name").asText();
      assetNames.put(assetName, asset);
    }
    List<Entry<String, JsonNode>> sortedAssets = assetNames.entrySet().stream()
      .filter(s -> architecture.isMatch(s.getKey()))
      .sorted(Comparator.comparingLong(o -> o.getValue().get("size").asLong()))
      .toList();

    if (!sortedAssets.isEmpty()) {
      return sortedAssets.get(sortedAssets.size() - 1).getValue();
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

  @Override
  public String getServerUrl() {
    Environment env = context.getBean(Environment.class);
    int port = env.getRequiredProperty("server.port", Integer.class);
    try {
      if (StringUtils.isNotEmpty(ContextNetwork.ping("homio.local", port))) {
        return "http://homio.local";
      }
    } catch (ConnectException ignore) {
    }
    boolean cloudOnline = context.getBean(CloudService.class).getCurrentEntity().getStatus().isOnline();
    if (cloudOnline) {
      return "https://homio.org";
    }
    return MACHINE_IP_ADDRESS + ":" + port;
  }

  @RequiredArgsConstructor
  public enum Architecture {
    armv6l(s -> s.contains("linux_armv6")),
    armv7l(s -> s.contains("linux_armv7")),
    arm32v6(s -> s.contains("arm32v6") || s.contains("arm6")),
    arm32v7(s -> s.contains("arm32v7") || s.contains("arm7")),
    arm32v8(s -> s.contains("arm32v8") || s.contains("arm8")),
    arm64v8(s -> s.contains("arm64v8")),
    aarch64(s -> (s.contains("linux_arm64v8") || s.contains("linux-aarch64"))),
    amd64(s -> (s.contains("amd64") || s.contains("linux64") || s.contains("linux-64"))),
    i386(s -> s.contains("i386")),
    win32(s -> s.contains("win32")),
    winArm64(s -> s.contains("win_arm64")),
    win64(s -> s.contains("win"));

    public final Predicate<String> matchName;

    public boolean isMatch(String platform) {
      return matchName.test(platform);
    }
  }

  @Getter
  @AllArgsConstructor
  public static class ProcessStatImpl implements ProcessStat {

    private final double cpuUsage;
    private final double memUsage;
    private final long mem;
  }
}
