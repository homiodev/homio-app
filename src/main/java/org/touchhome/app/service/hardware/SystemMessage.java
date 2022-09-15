package org.touchhome.app.service.hardware;

import com.sun.management.OperatingSystemMXBean;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.touchhome.bundle.api.inmemory.InMemoryDBEntity;

@Getter
@Setter
@NoArgsConstructor
public class SystemMessage extends InMemoryDBEntity {
    private double scl;
    private long mem;
    private double cpl;

    public SystemMessage(OperatingSystemMXBean osBean, long totalMemory) {
        this.cpl = osBean.getProcessCpuLoad() * 100;
        this.mem = totalMemory - osBean.getFreePhysicalMemorySize();
        this.scl = osBean.getSystemCpuLoad() * 100;
    }
}
