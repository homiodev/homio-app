package org.touchhome.app.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.ui.dialog.DialogModel;

@Getter
@Setter
@Accessors(chain = true)
public class HeaderButtonNotification extends BaseNotificationModel<HeaderButtonNotification> {

  private final Set<DialogModel> dialogs = new LinkedHashSet<>();
  private String color;
  private Integer duration;
  private String icon;
  private Boolean iconRotate;
  private Integer border;
  private String stopAction;
  private String page;

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
