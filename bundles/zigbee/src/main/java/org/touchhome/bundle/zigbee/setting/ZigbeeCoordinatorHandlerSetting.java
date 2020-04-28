package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.util.ApplicationContextHolder;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.handler.CC2531Handler;

import java.util.List;
import java.util.stream.Collectors;

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
    public List<Option> loadAvailableValues() {
        return ApplicationContextHolder.getBeansOfType(ZigBeeCoordinatorHandler.class)
                .stream().map(zb -> Option.key(zb.getClass().getSimpleName())).collect(Collectors.toList());
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public ZigBeeCoordinatorHandler parseValue(String value) {
        return ApplicationContextHolder.getBean(value, ZigBeeCoordinatorHandler.class);
    }
}
