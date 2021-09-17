package org.touchhome.app.manager.common.v1.item;

import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;

public interface UIInputEntityActionHandler extends UIInputEntity {
    UIActionHandler getActionHandler();
}
