package org.homio.app.builder.ui;

import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.jetbrains.annotations.NotNull;

public interface UIInputEntityActionHandler extends UIInputEntity {

  UIActionHandler getActionHandler();

  Object setActionHandler(@NotNull UIActionHandler action);
}
