package org.homio.app.service.scan;

import lombok.RequiredArgsConstructor;
import org.homio.api.Context;
import org.homio.api.service.discovery.ItemDiscoverySupport;

import java.util.ArrayList;
import java.util.List;

/**
 * BaseItemsDiscovery successor that creates list of DevicesScanner based on declared beans
 */
@RequiredArgsConstructor
public class BeansItemsDiscovery extends BaseItemsDiscovery {

  private final Class<? extends ItemDiscoverySupport> declaredBeanClass;

  @Override
  protected List<DevicesScanner> getScanners(Context context) {
    List<DevicesScanner> list = new ArrayList<>();
    for (ItemDiscoverySupport bean : context.getBeansOfType(declaredBeanClass)) {
      DevicesScanner devicesScanner = new DevicesScanner(bean.getName(), bean::scan);
      list.add(devicesScanner);
    }
    return list;
  }

  @Override
  protected String getBatchName() {
    return declaredBeanClass.getSimpleName();
  }
}
