package org.touchhome.bundle.cloud;

import org.touchhome.bundle.api.json.NotificationEntityJSON;

import java.util.Set;

public interface CloudProvider {
    String getStatus();

    Set<NotificationEntityJSON> getNotifications();
}
