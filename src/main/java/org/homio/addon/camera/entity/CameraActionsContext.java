package org.homio.addon.camera.entity;

import org.homio.api.EntityContext;
import org.homio.api.state.State;

public interface CameraActionsContext<T extends BaseCameraEntity> {

    State getAttribute(String key);

    T getEntity();

    EntityContext getEntityContext();
}
