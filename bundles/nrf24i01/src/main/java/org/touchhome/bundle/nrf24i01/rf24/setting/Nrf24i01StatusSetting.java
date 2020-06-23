package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.util.NotificationType;

import java.util.Collections;
import java.util.List;

public class Nrf24i01StatusSetting implements BundleSettingPlugin<DeviceStatus> {
    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public List<NotificationEntityJSON> buildHeaderNotificationEntity(DeviceStatus deviceStatus, EntityContext entityContext) {
        return Collections.singletonList(new NotificationEntityJSON("nrf24i01-status")
                .setName("NRF24I01 status")
                .setDescription(entityContext.getSettingValue(Nrf24i01StatusMessageSetting.class))
                .setNotificationType(deviceStatus == DeviceStatus.ONLINE ? NotificationType.info : NotificationType.danger));
    }
}
