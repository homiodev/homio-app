package org.touchhome.app.service.hardware;

import com.sun.management.OperatingSystemMXBean;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.touchhome.bundle.api.inmemory.InMemoryDBEntity;

import static org.touchhome.app.utils.InternalUtil.GB_DIVIDER;

@Getter
@Setter
@NoArgsConstructor
public class SystemMessage extends InMemoryDBEntity {
    private float scl;
    private float mem;
    private float cpl;

    public SystemMessage(OperatingSystemMXBean osBean, long totalMemory) {
        super();
        this.cpl = (float) (osBean.getProcessCpuLoad() * 100);

        this.mem = (float) ((totalMemory - osBean.getFreePhysicalMemorySize()) / GB_DIVIDER);
        this.scl = (float) (osBean.getSystemCpuLoad() * 100);
    }
}
