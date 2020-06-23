package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.util.NotificationType;

import java.util.Collections;
import java.util.List;

public class ZigbeeStatusSetting implements BundleSettingPlugin<DeviceStatus> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public String getDefaultValue() {
        return DeviceStatus.UNKNOWN.toString();
    }

    @Override
    public DeviceStatus parseValue(EntityContext entityContext, String value) {
        return value == null ? DeviceStatus.UNKNOWN : DeviceStatus.valueOf(value);
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public List<NotificationEntityJSON> buildHeaderNotificationEntity(DeviceStatus deviceStatus, EntityContext entityContext) {
        return Collections.singletonList(new NotificationEntityJSON("zigbee-status")
                .setName("Zigbee status: " + deviceStatus)
                .setDescription(entityContext.getSettingValue(ZigbeeStatusMessageSetting.class))
                .setNotificationType(deviceStatus == DeviceStatus.ONLINE ? NotificationType.info : NotificationType.danger));
    }
}
