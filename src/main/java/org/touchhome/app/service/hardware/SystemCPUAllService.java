package org.touchhome.app.service.hardware;

import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextStorage;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent;

@Component
@UIFieldSelectionParent(value = "selection.hardware", icon = "fas fa-microchip", iconColor = "#31BDB6", description = "SYSTEM_HARDWARE_SERVICES")
public class SystemCPUAllService extends BaseSystemService {

  public SystemCPUAllService() {
    super("scl", "SYS_CPU_ALL", "SYS.CPU_ALL_USAGE",
        "SYS.CPU_ALL_AGGR", "SYS.CPU_ALL_TS");
  }

  @Override
  public Object getStatusValue(GetStatusValueRequest request) {
    return EntityContextStorage.cpuStorage.getLatest().getScl();
  }
}
