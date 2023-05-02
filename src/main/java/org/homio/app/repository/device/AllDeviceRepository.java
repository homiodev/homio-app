package org.homio.app.repository.device;

import org.homio.bundle.api.entity.DeviceBaseEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public <T extends DeviceBaseEntity> @Nullable T getByIeeeAddressOrName(String name) {
        return (T) em.createQuery("FROM DeviceBaseEntity where ieeeAddress = :value OR name = :value", DeviceBaseEntity.class)
                     .setParameter("value", name).getResultList().stream().findAny().orElse(null);
    }
}
