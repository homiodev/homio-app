package org.touchhome.bundle.cloud.setting;

import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.notification.NotificationType;
import org.touchhome.bundle.cloud.impl.ServerConnectionStatus;

public class CloudServerConnectionStatusSetting implements BundleSettingPlugin<ServerConnectionStatus> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public ServerConnectionStatus parseValue(String value) {
        return StringUtils.isEmpty(value) ? null : ServerConnectionStatus.valueOf(value);
    }

    @Override
    public NotificationEntityJSON buildHeaderNotificationEntity(ServerConnectionStatus serverConnectionStatus, EntityContext entityContext) {
        return new NotificationEntityJSON("cloud-status")
                .setName("Cloud status")
                .setDescription(entityContext.getSettingValue(CloudServerConnectionMessageSetting.class))
                .setNotificationType(serverConnectionStatus == ServerConnectionStatus.CONNECTED ? NotificationType.info : NotificationType.danger);
    }
}
