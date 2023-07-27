package org.homio.app.spring;

import org.homio.api.EntityContext;

/**
 * Interface may be implemented and engine calls onContextUpdate() at startup and every time when new external been added/removed
 */
public interface ContextRefreshed {

    /**
     * Fires every time when new addon has been added to context or removed. Also fires at app startup after postConstruct()
     */
    void onContextRefresh(EntityContext entityContext) throws Exception;
}
