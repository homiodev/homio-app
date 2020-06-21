package org.touchhome.bundle.cloud.setting;

import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.cloud.impl.ServerConnectionStatus;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
    public ServerConnectionStatus parseValue(EntityContext entityContext, String value) {
        return StringUtils.isEmpty(value) ? null : ServerConnectionStatus.valueOf(value);
    }

    @Override
    public List<NotificationEntityJSON> buildHeaderNotificationEntity(ServerConnectionStatus serverConnectionStatus, EntityContext entityContext) {
        /*return new NotificationEntityJSON("cloud-status")
                .setName("Cloud status")
                .setDescription(entityContext.getSettingValue(CloudServerConnectionMessageSetting.class))
                .setNotificationType(serverConnectionStatus == ServerConnectionStatus.CONNECTED ? NotificationType.info : NotificationType.danger);*/
        List<NotificationEntityJSON> res = new ArrayList<>();
        if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome"))) {
            res.add(NotificationEntityJSON.danger("private-key").setName("Private Key").setDescription("Private key not found"));
        }
        if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome.pub"))) {
            res.add(NotificationEntityJSON.danger("public-key").setName("Public Key").setDescription("Public key not found"));
        }
        String serviceStatus = entityContext.getBean(LinuxHardwareRepository.class).getServiceStatus("touchhome-tunnel");
        if (!"active".equals(serviceStatus)) {
            res.add(NotificationEntityJSON.danger("cloud-status").setName("Cloud status").setDescription("cloud connection not active: " + serviceStatus));
        } else {
            res.add(NotificationEntityJSON.info("cloud-status").setName("Cloud status").setDescription("cloud connected"));
        }

        return res;
    }
}
