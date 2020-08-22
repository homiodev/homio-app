package org.touchhome.app.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.util.NotificationType;

@Getter
@Accessors(chain = true)
public class AlwaysOnTopNotificationEntityJSON extends NotificationEntityJSON {

    private final Boolean alwaysOnTop = true;

    @Setter
    private String color;

    @Setter
    private Integer duration;

    @Setter
    private String icon;

    @Setter
    private String stopAction;

    @Setter
    private Boolean remove = false;

    public AlwaysOnTopNotificationEntityJSON(NotificationEntityJSON notificationEntityJSON) {
        super(notificationEntityJSON.getEntityID());
        setName(notificationEntityJSON.getName());
        setDescription(notificationEntityJSON.getDescription());
        setNotificationType(notificationEntityJSON.getNotificationType());
    }

    @Override
    public AlwaysOnTopNotificationEntityJSON setName(String name) {
        return (AlwaysOnTopNotificationEntityJSON) super.setName(name);
    }

    @Override
    public AlwaysOnTopNotificationEntityJSON setDescription(String description) {
        return (AlwaysOnTopNotificationEntityJSON) super.setDescription(description);
    }

    @Override
    public AlwaysOnTopNotificationEntityJSON setNotificationType(NotificationType notificationType) {
        return (AlwaysOnTopNotificationEntityJSON) super.setNotificationType(notificationType);
    }
}
