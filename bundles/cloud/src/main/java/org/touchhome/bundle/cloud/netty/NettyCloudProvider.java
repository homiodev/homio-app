package org.touchhome.bundle.cloud.netty;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.cloud.CloudProvider;
import org.touchhome.bundle.cloud.netty.impl.ServerConnectionStatus;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionMessageSetting;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionStatusSetting;
import org.touchhome.bundle.cloud.setting.CloudServerUrlSetting;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NettyCloudProvider implements CloudProvider {

    private final EntityContext entityContext;

    @Override
    public String getStatus() {
        String error = entityContext.getSettingValue(CloudServerConnectionMessageSetting.class);
        ServerConnectionStatus status = entityContext.getSettingValue(CloudServerConnectionStatusSetting.class);
        return status.name() + ". Errors: " + error + ". Url: " + entityContext.getSettingValue(CloudServerUrlSetting.class);
    }

    @Override
    public Set<NotificationEntityJSON> getNotifications() {
        UserEntity user = entityContext.getUser();
        Set<NotificationEntityJSON> notifications = new HashSet<>();
        if (user != null && user.getKeystore() == null) {
            notifications.add(NotificationEntityJSON.danger("keystore").setName("Keystore").setDescription("Keystore not found"));
        }
        return notifications;
    }
}
