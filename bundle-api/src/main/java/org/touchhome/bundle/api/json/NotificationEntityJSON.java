package org.touchhome.bundle.api.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.util.NotificationType;

import java.util.Date;
import java.util.Objects;

@Getter
@Accessors(chain = true)
public class NotificationEntityJSON {

    private final String entityID;

    @Setter
    private String name;

    @Setter
    private String description;

    @Setter
    private NotificationType notificationType = NotificationType.info;

    private Date creationTime = new Date();

    public NotificationEntityJSON(String entityID) {
        if (entityID == null) {
            throw new IllegalArgumentException("entityId is null");
        }
        this.entityID = entityID;
    }

    public static NotificationEntityJSON danger(String entityID) {
        return new NotificationEntityJSON(entityID).setNotificationType(NotificationType.danger);
    }

    public static NotificationEntityJSON info(String entityID) {
        return new NotificationEntityJSON(entityID).setNotificationType(NotificationType.info);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NotificationEntityJSON that = (NotificationEntityJSON) o;
        return Objects.equals(entityID, that.entityID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityID);
    }
}
