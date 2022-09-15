package org.touchhome.app.service.hardware;

import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextStorage;

@Component
public class SystemCPUService extends BaseSystemService {

    public SystemCPUService() {
        super("cpl", "SYS_CPU", "SYS.CPU_USAGE", "SYS.CPU_AGGR", "SYS.CPU_TS");
    }

    @Override
    public Object getStatusValue(GetStatusValueRequest request) {
        return EntityContextStorage.cpuStorage.getLatest().getCpl();
    }
}
