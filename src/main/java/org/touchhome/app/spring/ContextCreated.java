package org.touchhome.app.spring;

import org.touchhome.app.manager.common.EntityContextImpl;

/** Interface may be implemented and engine calls init() at startup */
public interface ContextCreated {

    /** Fires only once after all beans had been constructed and all relations had been set */
    void onContextCreated(EntityContextImpl entityContext) throws Exception;
}
