package org.touchhome.app.repository.device;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class AllDeviceRepository extends AbstractRepository<DeviceBaseEntity> {

    public AllDeviceRepository() {
        super(DeviceBaseEntity.class);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceBaseEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }
}
