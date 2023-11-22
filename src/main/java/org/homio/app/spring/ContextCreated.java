package org.homio.app.spring;

import org.homio.app.manager.common.ContextImpl;

/**
 * Interface may be implemented and engine calls init() at startup
 */
public interface ContextCreated {

    /**
     * Fires only once after all beans had been constructed and all relations had been set
     */
    void onContextCreated(ContextImpl context) throws Exception;
}
