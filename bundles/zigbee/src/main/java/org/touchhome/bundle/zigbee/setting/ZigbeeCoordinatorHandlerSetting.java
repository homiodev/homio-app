package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.handler.CC2531Handler;

import java.util.List;

public class ZigbeeCoordinatorHandlerSetting implements BundleSettingPlugin<ZigBeeCoordinatorHandler> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxDynamic;
    }

    @Override
    public String getDefaultValue() {
        return CC2531Handler.class.getSimpleName();
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.simpleNamelist(entityContext.getBeansOfType(ZigBeeCoordinatorHandler.class));
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public ZigBeeCoordinatorHandler parseValue(EntityContext entityContext, String value) {
        return entityContext.getBean(value, ZigBeeCoordinatorHandler.class);
    }
}
