package org.homio.app.repository.device;

import org.homio.api.entity.DeviceBaseEntity;
import org.homio.api.repository.AbstractRepository;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class AllDeviceRepository extends AbstractRepository<DeviceBaseEntity> {

    public AllDeviceRepository() {
        super(DeviceBaseEntity.class);
    }

    @Override
    public DeviceBaseEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    public <T extends DeviceBaseEntity> @Nullable T getByIeeeAddressOrName(String name) {
        return emc.executeInTransaction(
            entityManager ->
                (T) entityManager.createQuery("FROM DeviceBaseEntity where ieeeAddress = :value OR name = :value", DeviceBaseEntity.class)
                                 .setParameter("value", name).getResultList().stream().findAny().orElse(null));
    }
}
