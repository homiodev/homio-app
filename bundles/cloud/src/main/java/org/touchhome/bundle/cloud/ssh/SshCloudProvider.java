package org.touchhome.bundle.cloud.ssh;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.cloud.CloudProvider;
import org.touchhome.bundle.cloud.netty.impl.ServerConnectionStatus;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SshCloudProvider implements CloudProvider {

    private final LinuxHardwareRepository linuxHardwareRepository;

    @Override
    public String getStatus() {
        int serviceStatus = linuxHardwareRepository.getServiceStatus("touchhome-tunnel");
        return serviceStatus == 0 ? ServerConnectionStatus.CONNECTED.name() : ServerConnectionStatus.DISCONNECTED_WIDTH_ERRORS.name();
    }

    @Override
    public Set<NotificationEntityJSON> getNotifications() {
        Set<NotificationEntityJSON> notifications = new HashSet<>();
        if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome"))) {
            notifications.add(NotificationEntityJSON.danger("private-key").setName("Private Key not found"));
        }
        if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome.pub"))) {
            notifications.add(NotificationEntityJSON.danger("public-key").setName("Public key not found"));
        }
        int serviceStatus = linuxHardwareRepository.getServiceStatus("touchhome-tunnel");
        if (serviceStatus == 0) {
            notifications.add(NotificationEntityJSON.info("cloud-status").setName("Cloud connected"));
        } else {
            notifications.add(NotificationEntityJSON.danger("cloud-status").setName("Cloud connection status not active " + serviceStatus));
        }
        return notifications;
    }
}
