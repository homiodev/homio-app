package org.homio.app.service;

import static org.homio.hquery.hardware.other.MachineHardwareRepository.osBean;

import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.app.model.entity.LocalBoardEntity;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class LocalBoardService extends ServiceInstance<LocalBoardEntity>
    implements HasEntityIdentifier {

    public static final long TOTAL_MEMORY = osBean.getTotalMemorySize();

    private String cpuUsageID;
    private String javaCpuUsageID;
    private String memID;
    private int cpuFetchInterval;

    public LocalBoardService(@NotNull EntityContext entityContext, @NotNull LocalBoardEntity entity) {
        super(entityContext, entity, true);
    }

    @Override
    protected void firstInitialize() {
        entityContext.var().createGroup("hardware", "Hardware", true, new Icon("fas fa-microchip",
            "#31BDB6"), "sys.hardware");

        this.cpuUsageID = entityContext.var().createVariable("hardware", "sys_cpu_load", "sys.cpu_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.cpu_load_description")
                       .setIcon(new Icon("fas fa-fa-microchip", "#A65535"))
                       .setNumberRange(0, 100).setUnit("%").setColor("#7B37B0"));

        this.javaCpuUsageID = entityContext.var().createVariable("hardware", "java_cpu_load", "sys.java_cpu_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.java_cpu_load_description")
                       .setIcon(new Icon("fas fa-fa-microchip", "#35A680"))
                       .setNumberRange(0, 100).setUnit("%").setColor("#B03780"));

        this.memID = entityContext.var().createVariable("hardware", "sys_mem_load", "sys.mem_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.mem_load_description")
                       .setIcon(new Icon("fas fa-fa-memory", "#7B37B0"))
                       .setNumberRange(0, 100).setUnit("%").setColor("#939C35"));

        this.initialize();
    }

    @Override
    protected void initialize() {
        if (cpuFetchInterval != entity.getCpuFetchInterval()) {
            cpuFetchInterval = entity.getCpuFetchInterval();

            entityContext
                .bgp().builder("hardware-cpu")
                .interval(Duration.ofSeconds(cpuFetchInterval))
                .execute(() -> {
                    entityContext.var().set(cpuUsageID, round100((float) (osBean.getCpuLoad() * 100F)));
                    entityContext.var().set(javaCpuUsageID, round100((float) (osBean.getProcessCpuLoad() * 100F)));
                    float memPercent = (TOTAL_MEMORY - osBean.getFreeMemorySize()) / (float) TOTAL_MEMORY * 100F;
                    entityContext.var().set(memID, round100(memPercent));
                });
        }
    }

    private float round100(float input) {
        return Math.round(input * 100.0) / 100.0F;
    }

    @Override
    public void destroy() {
    }
}
