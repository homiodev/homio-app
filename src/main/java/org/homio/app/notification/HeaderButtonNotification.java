package org.homio.app.notification;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.ui.dialog.DialogModel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
@EqualsAndHashCode
public class HeaderButtonNotification extends BaseNotificationModel<HeaderButtonNotification> {

    private final Set<DialogModel> dialogs = new LinkedHashSet<>();
    private Integer duration;

    private String icon;
    private String iconColor;

    private Integer borderWidth = 1;
    private String borderColor;

    private String handleActionID;
    private String page;
    private Supplier<ActionResponseModel> clickAction;
    private String attachToHeaderMenu;

    public HeaderButtonNotification(String entityID) {
        super(entityID);
    }

    @Override
    public HeaderButtonNotification setTitle(String name) {
        return (HeaderButtonNotification) super.setTitle(name);
    }

    @Override
    public HeaderButtonNotification setValue(Object value) {
        return (HeaderButtonNotification) super.setValue(value);
    }
}
