package org.homio.app.repository;

import org.homio.api.model.HasEntityIdentifier;

public interface PureRepository<T extends HasEntityIdentifier> {
    void flushCashedEntity(T entity);

    Class<T> getEntityClass();
}
