package org.touchhome.app.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.NotificationModel;
import org.touchhome.bundle.api.util.NotificationLevel;

@Getter
@Setter
@Accessors(chain = true)
public class AlwaysOnTopNotificationModel extends NotificationModel {

    private final Boolean alwaysOnTop = true;

    private String color;

    private Integer duration;

    private String icon;

    private String stopAction;

    private Boolean remove = false;

    public AlwaysOnTopNotificationModel(String entityID) {
        super(entityID);
    }

    public AlwaysOnTopNotificationModel(NotificationModel json, String color, Integer duration, String icon) {
        this(json);
        this.color = color;
        this.duration = duration;
        this.icon = icon;
    }

    public AlwaysOnTopNotificationModel(NotificationModel json) {
        super(json.getEntityID());
        setTitle(json.getTitle());
        setValue(json.getValue());
        setLevel(json.getLevel());
    }

    @Override
    public AlwaysOnTopNotificationModel setTitle(String name) {
        return (AlwaysOnTopNotificationModel) super.setTitle(name);
    }

    @Override
    public AlwaysOnTopNotificationModel setValue(Object value) {
        return (AlwaysOnTopNotificationModel) super.setValue(value);
    }

    public AlwaysOnTopNotificationModel setLevel(NotificationLevel level) {
        return (AlwaysOnTopNotificationModel) super.setLevel(level);
    }
}
