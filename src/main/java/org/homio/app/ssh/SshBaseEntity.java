package org.homio.app.ssh;

import org.homio.api.entity.types.IdentityEntity;
import org.homio.api.service.EntityService;

/**
 * Base class for all ssh entities to allow to connect to it
 *
 * @param <T> - actual entity
 * @param <S> - service
 */
@SuppressWarnings({"rawtypes"})
public abstract class SshBaseEntity<T extends SshBaseEntity, S extends EntityService.ServiceInstance & SshProviderService<T>> extends IdentityEntity
        implements EntityService<S, T> {
}
