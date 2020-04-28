package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.notification.NotificationType;

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
    public DeviceStatus parseValue(String value) {
        return value == null ? DeviceStatus.UNKNOWN : DeviceStatus.valueOf(value);
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public NotificationEntityJSON buildHeaderNotificationEntity(DeviceStatus deviceStatus, EntityContext entityContext) {
        return new NotificationEntityJSON("zigbee-status")
                .setName("Zigbee status: " + deviceStatus)
                .setDescription(entityContext.getSettingValue(ZigbeeStatusMessageSetting.class))
                .setNotificationType(deviceStatus == DeviceStatus.ONLINE ? NotificationType.info : NotificationType.danger);
    }
}
