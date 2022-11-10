package org.touchhome.app.service.hardware;

import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextStorage;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent;

@Component
@UIFieldSelectionParent("selection.hardware")
public class SystemMemService extends BaseSystemService {

  public SystemMemService() {
    super("mem", "SYS_MEM", "SYS.MEM_FREE", "SYS.MEM_AGGR", "SYS.MEM_TS");
  }

  @Override
  public Object getStatusValue(GetStatusValueRequest request) {
    return EntityContextStorage.cpuStorage.getLatest().getMem();
  }
}
