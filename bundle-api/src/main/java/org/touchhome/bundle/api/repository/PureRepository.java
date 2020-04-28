package org.touchhome.bundle.api.repository;

import org.touchhome.bundle.api.model.PureEntity;

public interface PureRepository<T extends PureEntity> {
    void flushCashedEntity(T entity);

    Class<T> getEntityClass();
}
