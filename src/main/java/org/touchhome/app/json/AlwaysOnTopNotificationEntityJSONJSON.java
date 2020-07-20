package org.touchhome.app.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.util.NotificationType;

@Getter
@Accessors(chain = true)
public class AlwaysOnTopNotificationEntityJSONJSON extends NotificationEntityJSON {

    private final Boolean alwaysOnTop = true;

    @Setter
    private String color;

    @Setter
    private Integer duration;

    @Setter
    private Boolean remove = false;

    public AlwaysOnTopNotificationEntityJSONJSON(NotificationEntityJSON notificationEntityJSON) {
        super(notificationEntityJSON.getEntityID());
        setName(notificationEntityJSON.getName());
        setDescription(notificationEntityJSON.getDescription());
        setNotificationType(notificationEntityJSON.getNotificationType());
    }

    @Override
    public AlwaysOnTopNotificationEntityJSONJSON setName(String name) {
        return (AlwaysOnTopNotificationEntityJSONJSON) super.setName(name);
    }

    @Override
    public AlwaysOnTopNotificationEntityJSONJSON setDescription(String description) {
        return (AlwaysOnTopNotificationEntityJSONJSON) super.setDescription(description);
    }

    @Override
    public AlwaysOnTopNotificationEntityJSONJSON setNotificationType(NotificationType notificationType) {
        return (AlwaysOnTopNotificationEntityJSONJSON) super.setNotificationType(notificationType);
    }
}
