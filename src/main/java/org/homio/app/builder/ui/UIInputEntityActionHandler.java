package org.homio.app.builder.ui;

import org.homio.bundle.api.ui.action.UIActionHandler;
import org.homio.bundle.api.ui.field.action.v1.UIInputEntity;

public interface UIInputEntityActionHandler extends UIInputEntity {

    UIActionHandler getActionHandler();
}
