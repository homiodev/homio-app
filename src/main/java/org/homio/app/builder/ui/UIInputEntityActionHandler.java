package org.homio.app.builder.ui;

import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIInputEntity;

public interface UIInputEntityActionHandler extends UIInputEntity {

  UIActionHandler getActionHandler();
}
