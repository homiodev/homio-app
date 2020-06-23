package org.touchhome.bundle.api.repository;

import org.touchhome.bundle.api.model.HasIdIdentifier;

public interface PureRepository<T extends HasIdIdentifier> {
    void flushCashedEntity(T entity);

    Class<T> getEntityClass();
}
