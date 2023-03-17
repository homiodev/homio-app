package org.touchhome.app.manager.common;

import static org.touchhome.app.utils.InternalUtil.GB_DIVIDER;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.setting.system.SystemCPUFetchValueIntervalSetting;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.EntityContextSetting;
import org.touchhome.bundle.api.EntityContextSetting.MemSetterHandler;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasStatusAndMsg;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.Status;

@Log4j2
@RequiredArgsConstructor
public class EntityContextStorage {

    public static final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    public static final Map<String, EntityMemoryData> ENTITY_MEMORY_MAP = new ConcurrentHashMap<>();
    public static final long TOTAL_MEMORY = osBean.getTotalPhysicalMemorySize();
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
        if (value instanceof Status) {
            Status status = (Status) value;
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

    public void init() {
        initSystemCpuListening();
    }

    @SneakyThrows
    private void initSystemCpuListening() {
        entityContext.var().createGroup("hardware", "Hardware", true, "fas fa-microchip",
            "#31BDB6", "sys.hardware");
        String cpuUsageID = entityContext.var().createVariable("hardware", "sys_cpu_load", "sys.cpu_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.cpu_load_description").setReadOnly(true).setUnit("%").setColor("#7B37B0"));
        String javaCpuUsageID = entityContext.var().createVariable("hardware", "java_cpu_load", "sys.java_cpu_load",
            VariableType.Float, builder ->
                builder.setDescription("sys.java_cpu_load_description").setReadOnly(true).setUnit("%").setColor("#B03780"));
        String memID = entityContext.var().createVariable("hardware", "sys_mem", "sys.memory",
            VariableType.Float, builder ->
                builder.setDescription("sys.memory_description").setReadOnly(true).setColor("#939C35"));

        entityContext.setting().listenValueAndGet(SystemCPUFetchValueIntervalSetting.class,
            "hardware-cpu",
            timeout -> {
                if (this.hardwareCpuScheduler != null) {
                    this.hardwareCpuScheduler.cancel();
                }
                this.hardwareCpuScheduler = entityContext.bgp().builder("hardware-cpu").interval(Duration.ofSeconds(timeout)).execute(
                    () -> {
                        entityContext.var().set(cpuUsageID, (float) (osBean.getSystemCpuLoad() * 100));
                        entityContext.var().set(javaCpuUsageID, (float) (osBean.getProcessCpuLoad() * 100));
                        entityContext.var().set(memID, (float) ((TOTAL_MEMORY - osBean.getFreePhysicalMemorySize()) / GB_DIVIDER));
                    });
            });
    }

    public void remove(String entityID) {
        ENTITY_MEMORY_MAP.remove(entityID);
    }

    private static class EntityMemoryData {

        private final Map<String, Object> VALUE_MAP = new ConcurrentHashMap<>();
    }
}
