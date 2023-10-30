package org.homio.addon.esphome;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import java.net.URL;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tasmota.ESPHomeDeviceEntity;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ESPHomeEntrypoint implements AddonEntrypoint {

    public static final String ESPHOME_ICON = "fas fa-microchip";
    public static final String ESPHOME_COLOR = "#494D4F";

    private final Context context;

    @Override
    public void init() {
        ensureEntityExists(context);
        context.setting().listenValue(ESPHomeCompactModeSetting.class, "esphome-compact-mode",
            (value) -> context.ui().updateItems(ESPHomeDeviceEntity.class));
    }

    @SneakyThrows
    public URL getAddonImageURL() {
        return getResource("images/esphome.png");
    }

    public void ensureEntityExists(Context context) {
        ESPHomeProjectEntity entity = context.db().getEntity(ESPHomeProjectEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new ESPHomeProjectEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setName("ESPHome");
            entity.setMqttEntity(context.service().getPrimaryMqttEntity());
            context.db().save(entity, false);
        }
    }
}
