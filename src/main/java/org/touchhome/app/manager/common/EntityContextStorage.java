package org.touchhome.app.manager.common;

import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.service.hardware.SystemMessage;
import org.touchhome.app.setting.system.SystemCPUFetchValueIntervalSetting;
import org.touchhome.app.setting.system.SystemCPUHistorySizeSetting;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.inmemory.InMemoryDB;
import org.touchhome.bundle.api.inmemory.InMemoryDBService;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class EntityContextStorage {
    private final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
            OperatingSystemMXBean.class);
    private final long TOTAL_MEMORY = osBean.getTotalPhysicalMemorySize();
    public static InMemoryDBService<SystemMessage> cpuStorage;

    private final EntityContextImpl entityContext;
    private EntityContextBGP.ThreadContext<Void> hardwareCpuScheduler;

    public void init() {
        initSystemCpuListening();
    }

    private void initSystemCpuListening() {
        cpuStorage = InMemoryDB.getService(SystemMessage.class,
                (long) entityContext.setting().getValue(SystemCPUHistorySizeSetting.class));
        entityContext.setting().listenValue(SystemCPUHistorySizeSetting.class, "listen-cpu-history", size -> {
            cpuStorage.updateQuota((long) size);
        });

        entityContext.setting().listenValueAndGet(SystemCPUFetchValueIntervalSetting.class, "hardware-cpu", timeout -> {
            if (this.hardwareCpuScheduler != null) {
                this.hardwareCpuScheduler.cancel();
            }
            this.hardwareCpuScheduler = entityContext.bgp().schedule("hardware-cpu", timeout, TimeUnit.SECONDS, () -> {
                SystemMessage systemMessage = new SystemMessage(osBean, TOTAL_MEMORY);
                cpuStorage.save(systemMessage);
                entityContext.event().fireEvent("cpu", systemMessage, false);
            }, true);
        });
    }
}
