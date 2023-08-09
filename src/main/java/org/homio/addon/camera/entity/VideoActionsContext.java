package org.homio.addon.camera.entity;

import org.homio.api.EntityContext;
import org.homio.api.state.State;

public interface VideoActionsContext<T extends BaseVideoEntity> {

    State getAttribute(String key);

    T getEntity();

    EntityContext getEntityContext();
}
