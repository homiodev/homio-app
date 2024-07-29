package org.homio.app.service.device;

import static org.homio.hquery.hardware.other.MachineHardwareRepository.osBean;

import java.time.Duration;
import java.util.Set;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.ContextVar.Variable;
import org.homio.api.ContextVar.VariableType;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.service.device.LocalBoardUsbListener.DiskInfo;
import org.jetbrains.annotations.NotNull;

public class LocalBoardService extends ServiceInstance<LocalBoardEntity>
    implements HasEntityIdentifier {

    public static final long TOTAL_MEMORY = osBean.getTotalMemorySize();
    private final @Getter LocalBoardUsbListener usbListener;

    private Variable cpuUsageVar;
    private Variable javaCpuUsageVar;
    private Variable memoryVar;
    private int cpuFetchInterval;

    public LocalBoardService(@NotNull Context context, @NotNull LocalBoardEntity entity) {
        super(context, entity, true, "Local board");
        this.setExposeService(true);
        this.usbListener = new LocalBoardUsbListener(context);
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
                    memoryVar.set(round100((TOTAL_MEMORY - osBean.getFreeMemorySize()) / (float) TOTAL_MEMORY * 100F));
                });
        }

        usbListener.listenUsbDevices();
    }

    private float round100(float input) {
        return Math.round(input * 100.0) / 100.0F;
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) {
    }
}
