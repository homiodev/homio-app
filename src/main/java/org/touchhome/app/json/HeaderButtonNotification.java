package org.touchhome.app.json;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.manager.common.impl.EntityContextUIImpl;
import org.touchhome.bundle.api.ui.BaseNotificationModel;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Accessors(chain = true)
public class HeaderButtonNotification extends BaseNotificationModel<HeaderButtonNotification> {

    private String color;

    private Integer duration;

    private String icon;

    private boolean iconRotate;

    private String stopAction;

    private final Set<EntityContextUIImpl.ConfirmationRequestModel> confirmations = new LinkedHashSet<>();

    public HeaderButtonNotification(String entityID) {
        super(entityID);
    }

   /* public HeaderButtonNotificationModel(NotificationModel json, String color, Integer duration, String icon) {
        this(json);
        this.color = color;
        this.duration = duration;
        this.icon = icon;
    }

    public HeaderButtonNotificationModel(NotificationModel json) {
        super(json.getEntityID());
        setTitle(json.getTitle());
        setValue(json.getValue());
        setLevel(json.getLevel());
    }*/

    @Override
    public HeaderButtonNotification setTitle(String name) {
        return (HeaderButtonNotification) super.setTitle(name);
    }

    @Override
    public HeaderButtonNotification setValue(Object value) {
        return (HeaderButtonNotification) super.setValue(value);
    }
}
