package org.homio.app.service.scan;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContext;
import org.homio.api.service.discovery.ItemDiscoverySupport;

/**
 * BaseItemsDiscovery successor that creates list of DevicesScanner based on declared beans
 */
@RequiredArgsConstructor
public class BeansItemsDiscovery extends BaseItemsDiscovery {

    private final Class<? extends ItemDiscoverySupport> declaredBeanClass;

    @Override
    protected List<DevicesScanner> getScanners(EntityContext entityContext) {
        List<DevicesScanner> list = new ArrayList<>();
        for (ItemDiscoverySupport bean : entityContext.getBeansOfType(declaredBeanClass)) {
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
