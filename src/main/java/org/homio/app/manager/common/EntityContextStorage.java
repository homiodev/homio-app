package org.homio.app.manager.common;

import static org.homio.app.utils.InternalUtil.GB_DIVIDER;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextSetting;
import org.homio.api.EntityContextSetting.MemSetterHandler;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.app.setting.system.SystemCPUFetchValueIntervalSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@RequiredArgsConstructor
public class EntityContextStorage {

    public static final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    public static final Map<String, EntityMemoryData> ENTITY_MEMORY_MAP = new ConcurrentHashMap<>();
    public static final long TOTAL_MEMORY = osBean.getTotalMemorySize();
    public static final double TOTAL_MEMORY_GB = osBean.getTotalMemorySize() / GB_DIVIDER;
    // constructor parameters
    private final EntityContextImpl entityContext;
    private EntityContextBGP.ThreadContext<Void> hardwareCpuScheduler;

    {
        EntityContextSetting.MEM_HANDLER.set(new MemSetterHandler() {
            @Override
            public void setValue(@NotNull HasEntityIdentifier entity, @NotNull String key, @NotNull String title, @Nullable Object value) {
                setMemValue(entity, key, title, value);
            }

            @Override
            public Object getValue(@NotNull HasEntityIdentifier entity, @NotNull String key, Object defaultValue) {
                EntityMemoryData data = ENTITY_MEMORY_MAP.computeIfAbsent(entity.getEntityID(), s -> new EntityMemoryData());
                return data.VALUE_MAP.getOrDefault(key, defaultValue);
            }
        });
    }

    public void init() {
        initSystemCpuListening();
    }

    public void remove(String entityID) {
        ENTITY_MEMORY_MAP.remove(entityID);
    }

    private void setMemValue(@NotNull HasEntityIdentifier entity, @NotNull String key, @NotNull String title, @Nullable Object value) {
        EntityMemoryData data = ENTITY_MEMORY_MAP.computeIfAbsent(entity.getEntityID(), s -> new EntityMemoryData());
        boolean sendUpdateToUI = entity instanceof HasStatusAndMsg && key.startsWith("status");
        if (value == null || (value instanceof String && value.toString().isEmpty())) {
            if (data.VALUE_MAP.remove(key) == null) {
                sendUpdateToUI = false;
            }
        } else {
            Object prevValue = data.VALUE_MAP.put(key, value);
            if (!Objects.equals(value, prevValue)) {
                logUpdatedValue(entity, key, title, value, data);
            } else {
                sendUpdateToUI = false;
            }
        }

        if (sendUpdateToUI) {
            entityContext.ui().updateItem((BaseEntity<?>) entity, key, value);
        }
    }

    private void logUpdatedValue(@NotNull HasEntityIdentifier entity, @NotNull String key, @NotNull String title, @NotNull Object value,
        EntityMemoryData data) {
        if (value instanceof Status status) {
            Level level = status == Status.ERROR ? Level.ERROR : Level.DEBUG;
            Object message = data.VALUE_MAP.get(key + "Message");
            if (message == null) {
                LogManager.getLogger(entity.getClass()).log(level, "[{}]: Set {} '{}' status: {}", entity.getEntityID(), entity, title, status);
            } else {
                LogManager.getLogger(entity.getClass())
                          .log(level, "[{}]: Set {} '{}' status: {}. Msg: {}", entity.getEntityID(), entity, title, status, message);
            }
        }
    }

    @SneakyThrows
    private void initSystemCpuListening() {
        entityContext.var().createGroup("hardware", "Hardware", true, new Icon("fas fa-microchip",
            "#31BDB6"), "sys.hardware");
        String cpuUsageID = entityContext.var().createVariable("hardware", "sys_cpu_load", "sys.cpu_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.cpu_load_description").setReadOnly(true).setUnit("%").setColor("#7B37B0"));
        String javaCpuUsageID = entityContext.var().createVariable("hardware", "java_cpu_load", "sys.java_cpu_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.java_cpu_load_description").setReadOnly(true).setUnit("%").setColor("#B03780"));
        String memID = entityContext.var().createVariable("hardware", "sys_mem_load", "sys.mem_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.mem_load_description").setReadOnly(true).setColor("#939C35"));
        entityContext.event().addEvent("sys_cpu_load");
        entityContext.event().addEvent("java_cpu_load");
        entityContext.event().addEvent("sys_mem_load");

        entityContext.setting().listenValueAndGet(SystemCPUFetchValueIntervalSetting.class,
            "hardware-cpu",
            timeout -> {
                if (this.hardwareCpuScheduler != null) {
                    this.hardwareCpuScheduler.cancel();
                }
                this.hardwareCpuScheduler = entityContext.bgp().builder("hardware-cpu").interval(Duration.ofSeconds(timeout)).execute(
                    () -> {
                        entityContext.var().set(cpuUsageID, round100((float) (osBean.getCpuLoad() * 100F)));
                        entityContext.var().set(javaCpuUsageID, round100((float) (osBean.getProcessCpuLoad() * 100F)));
                        float memPercent = (TOTAL_MEMORY - osBean.getFreeMemorySize()) / (float) TOTAL_MEMORY * 100F;
                        entityContext.var().set(memID, round100(memPercent));
                    });
            });
    }

    private float round100(float input) {
        return Math.round(input * 100.0) / 100.0F;
    }

    private static class EntityMemoryData {

        private final Map<String, Object> VALUE_MAP = new ConcurrentHashMap<>();
    }
}
