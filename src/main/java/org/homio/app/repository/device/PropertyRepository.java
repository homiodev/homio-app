package org.homio.app.repository.device;

import org.homio.api.entity.PropertyBaseEntity;
import org.homio.app.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository
public class PropertyRepository extends AbstractRepository<PropertyBaseEntity> {

    public PropertyRepository() {
        super(PropertyBaseEntity.class, PropertyBaseEntity.PREFIX);
    }
}
