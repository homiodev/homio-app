package org.homio.app.service.device;

import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextVar.Variable;
import org.homio.api.ContextVar.VariableType;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.service.device.LocalBoardUsbListener.DiskInfo;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Long.parseLong;
import static org.homio.hquery.hardware.other.MachineHardwareRepository.osBean;

@Getter
public class LocalBoardService extends ServiceInstance<LocalBoardEntity>
        implements HasEntityIdentifier {

    public static final long TOTAL_MEMORY = osBean.getTotalMemorySize();
    private final Pattern memTotalPattern = Pattern.compile("MemTotal:\\s+(\\d+) kB");
    private final Pattern memFreePattern = Pattern.compile("MemFree:\\s+(\\d+) kB");
    private final Pattern buffersPattern = Pattern.compile("Buffers:\\s+(\\d+) kB");
    private final Pattern cachedPattern = Pattern.compile("Cached:\\s+(\\d+) kB");

    private final LocalBoardUsbListener usbListener;
    private final MachineHardwareRepository hardwareRepo;

    private Variable cpuUsageVar;
    private Variable javaCpuUsageVar;
    private Variable memoryVar;
    private Variable netTXVar;
    private Variable netRXVar;
    private int cpuFetchInterval;

    private Map<String, NetworkStat> oldNetStat;
    private Map<String, NetworkStat> newNetStat;

    private Variable cpuTemp;
    private boolean ableToFetchCpuTemperature;

    public LocalBoardService(@NotNull Context context, @NotNull LocalBoardEntity entity) {
        super(context, entity, true, "Local board");
        this.setExposeService(true);
        this.hardwareRepo = context.getBean(MachineHardwareRepository.class);
        this.usbListener = new LocalBoardUsbListener(context);
    }

    @SneakyThrows
    private static Map<String, NetworkStat> getNetworkBytes() {
        Path net = Paths.get("/proc/net/dev");
        Map<String, NetworkStat> stats = new HashMap<>();
        for (String line : Files.readAllLines(net)) {
            line = line.trim();
            String[] items = line.split(":");
            if (items.length == 2) {
                String[] data = items[1].trim().split("\\s+");
                stats.put(items[0].trim(), new NetworkStat(parseLong(data[0]), parseLong(data[8])));
            }
        }
        return stats;
    }

    public Set<DiskInfo> getUsbDevices() {
        return usbListener.getUsbDevices();
    }

    public DiskInfo getUsbDevice(int alias) {
        return getUsbDevices().stream().filter(u -> u.getAlias() == alias).findAny().orElse(null);
    }

    @Override
    protected void firstInitialize() {
        this.cpuUsageVar = context.var().createVariable("hardware", "sys_cpu_load", "sys.cpu_load",
                VariableType.Float, builder ->
                        builder.setDescription("sys.cpu_load_description")
                                .setLocked(true)
                                .setIcon(new Icon("fas fa-microchip", "#A65535"))
                                .setNumberRange(0, 100).setUnit("%").setColor("#7B37B0"));

        this.javaCpuUsageVar = context.var().createVariable("hardware", "java_cpu_load", "sys.java_cpu_load",
                VariableType.Float, builder ->
                        builder.setDescription("sys.java_cpu_load_description")
                                .setLocked(true)
                                .setIcon(new Icon("fas fa-microchip", "#35A680"))
                                .setNumberRange(0, 100).setUnit("%").setColor("#B03780"));

        this.memoryVar = context.var().createVariable("hardware", "sys_mem_load", "sys.mem_load",
                VariableType.Float, builder ->
                        builder.setDescription("sys.mem_load_description")
                                .setIcon(new Icon("fas fa-memory", "#7B37B0"))
                                .setNumberRange(0, 100).setUnit("%").setColor("#939C35"));

        this.netRXVar = context.var().createVariable("hardware", "sys_net_rx", "sys.net_rx",
                VariableType.Float, builder ->
                        builder.setDescription("sys.net_rx_description")
                                .setIcon(new Icon("fas fa-arrow-right-arrow-left", "#37B04D"))
                                .setUnit("KB/s").setColor("#37B04D"));

        this.netTXVar = context.var().createVariable("hardware", "sys_net_rt", "sys.net_tx",
                VariableType.Float, builder ->
                        builder.setDescription("sys.net_tx_description")
                                .setIcon(new Icon("fas fa-arrow-right-arrow-left", "#3796B0"))
                                .setUnit("KB/s").setColor("#3796B0"));

        this.cpuTemp = context.var().createVariable("hardware", "sys_cpu_temp", "sys.cpu_temp",
                VariableType.Float, builder ->
                        builder.setDescription("sys.cpu_temp_description")
                                .setIcon(new Icon("fas fa-thermometer", "#3796B0"))
                                .setUnit("°C").setColor("#3796B0"));
        try {
            Double cpuTemperature = hardwareRepo.getCpuTemperature();
            ableToFetchCpuTemperature = cpuTemperature != null && cpuTemperature > 0;
        } catch (Exception ignore) {
        }

        this.initialize();
    }

    @Override
    protected void initialize() {
        if (cpuFetchInterval != entity.getCpuFetchInterval()) {
            cpuFetchInterval = entity.getCpuFetchInterval();

            context
                    .bgp().builder("hardware-cpu")
                    .interval(Duration.ofSeconds(cpuFetchInterval))
                    .execute(() -> {
                        cpuUsageVar.set(round100((float) (osBean.getCpuLoad() * 100F)));
                        javaCpuUsageVar.set(round100((float) (osBean.getProcessCpuLoad() * 100F)));
                        // float round100 = round100((TOTAL_MEMORY - osBean.getFreeMemorySize()) / (float) TOTAL_MEMORY * 100F);
                        Double usedMemory = getUsedMemory();
                        if (usedMemory != null) {
                            memoryVar.set(Math.round(usedMemory * 100.0) / 100.0);
                        }

                        if (ableToFetchCpuTemperature) {
                            try {
                                cpuTemp.set(hardwareRepo.getCpuTemperature());
                            } catch (Exception ignored) {
                            }
                        }
                    });

            if (SystemUtils.IS_OS_LINUX && Files.exists(Paths.get("/proc/net/dev"))) {
                newNetStat = getNetworkBytes();
                context.bgp().builder("net-stat")
                        .intervalWithDelay(Duration.ofSeconds(1))
                        .execute(() -> {
                            oldNetStat = newNetStat;
                            newNetStat = getNetworkBytes();
                            netTXVar.set(getNetworkDiff(networkStat -> networkStat.tx) / 1024);
                            netRXVar.set(getNetworkDiff(networkStat -> networkStat.rx) / 1024);
                        });
            }
        }
        usbListener.listenUsbDevices();
    }

    private Double getUsedMemory() throws IOException {
        if (SystemUtils.IS_OS_LINUX) {
            String memInfo = new String(Files.readAllBytes(Paths.get("/proc/meminfo")));
            long memTotal = extractValue(memInfo, memTotalPattern);
            long memFree = extractValue(memInfo, memFreePattern);
            long buffers = extractValue(memInfo, buffersPattern);
            long cached = extractValue(memInfo, cachedPattern);


            if (memTotal > 0) {
                long availableMemory = memFree + buffers + cached;
                long usedMemory = memTotal - availableMemory;
                return (usedMemory / (double) memTotal) * 100;
            }
        }
        return null;
    }

    public long getNetworkDiff(String iface, Function<NetworkStat, Long> handler) {
        return getNetworkDiff(s -> s.equals(iface), handler);
    }

    private long getNetworkDiff(Function<NetworkStat, Long> handler) {
        return getNetworkDiff(s -> s.startsWith("wlan") || s.startsWith("etch"), handler);
    }

    private long getNetworkDiff(Predicate<String> ifaceMatcher, Function<NetworkStat, Long> handler) {
        long value = 0;
        for (Map.Entry<String, NetworkStat> entry : newNetStat.entrySet()) {
            String iface = entry.getKey();
            if (ifaceMatcher.test(iface)) {
                NetworkStat networkStat = oldNetStat.get(iface);
                value += (handler.apply(entry.getValue()) - (networkStat == null ? 0 : handler.apply(networkStat)));
            }
        }
        return value;
    }

    private float round100(float input) {
        return Math.round(input * 100.0) / 100.0F;
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) {
    }

    private static long extractValue(String memInfo, Pattern pattern) {
        Matcher matcher = pattern.matcher(memInfo);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return -1;
    }

    public record NetworkStat(long rx, long tx) {
    }
}
