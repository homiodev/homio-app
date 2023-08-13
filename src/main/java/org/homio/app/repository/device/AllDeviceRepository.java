package org.homio.app.repository.device;

import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class AllDeviceRepository extends AbstractRepository<DeviceBaseEntity> {

    public AllDeviceRepository() {
        super(DeviceBaseEntity.class, "dvc_");
    }

    @Override
    public DeviceBaseEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    public <T extends DeviceBaseEntity> @Nullable T getByIeeeAddressOrName(String name) {
        String sql = "FROM DeviceBaseEntity where ieeeAddress = :value OR name = :value";
        return tmc.executeInTransactionReadOnly(em -> (T)
            em.createQuery(sql, DeviceBaseEntity.class)
              .setParameter("value", name)
              .getResultList().stream().findAny().orElse(null));
    }
}
