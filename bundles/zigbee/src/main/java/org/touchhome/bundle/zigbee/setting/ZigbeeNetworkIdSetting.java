package org.touchhome.bundle.zigbee.setting;

import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class ZigbeeNetworkIdSetting implements BundleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.TextSelectBoxDynamic;
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        try {
            return Files.walk(TouchHomeUtils.resolvePath("zigbee"), 1)
                    .filter(f -> Files.isDirectory(f) && !f.getFileName().toString().equals("zigbee"))
                    .map(f -> Option.key(f.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("Unable to fetch network ids", ex);
        }
        return Collections.emptyList();
    }
}
