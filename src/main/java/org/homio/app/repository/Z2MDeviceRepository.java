package org.homio.app.repository;

import java.util.ArrayList;
import java.util.List;
import org.homio.addon.z2m.model.Z2MDeviceEntity;
import org.homio.addon.z2m.model.Z2MLocalCoordinatorEntity;
import org.homio.addon.z2m.service.Z2MDeviceService;
import org.homio.api.EntityContext;
import org.springframework.stereotype.Repository;

@Repository
public class Z2MDeviceRepository extends AbstractRepository<Z2MDeviceEntity> {

    private final EntityContext entityContext;

    public Z2MDeviceRepository(EntityContext entityContext) {
        super(Z2MDeviceEntity.class);
        this.entityContext = entityContext;
    }

    @Override
    public List<Z2MDeviceEntity> listAll() {
        List<Z2MDeviceEntity> list = new ArrayList<>();
        for (Z2MLocalCoordinatorEntity coordinator : entityContext.findAll(Z2MLocalCoordinatorEntity.class)) {
            list.addAll(coordinator.getService().getDeviceHandlers().values().stream()
                                   .map(Z2MDeviceService::getDeviceEntity).toList());
        }
        return list;
    }

    @Override
    public Z2MDeviceEntity getByEntityID(String entityID) {
        for (Z2MLocalCoordinatorEntity coordinator : entityContext.findAll(Z2MLocalCoordinatorEntity.class)) {
            for (Z2MDeviceService deviceService :
                coordinator.getService().getDeviceHandlers().values()) {
                if (deviceService.getDeviceEntity().getEntityID().equals(entityID)) {
                    return deviceService.getDeviceEntity();
                }
            }
        }
        return null;
    }

    @Override
    public Z2MDeviceEntity save(Z2MDeviceEntity entity) {
        // ignore
        return entity;
    }

    @Override
    public Z2MDeviceEntity getByEntityIDWithFetchLazy(String entityID, boolean ignoreNotUILazy) {
        return getByEntityID(entityID);
    }
}
