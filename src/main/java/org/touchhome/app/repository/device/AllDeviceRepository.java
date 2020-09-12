package org.touchhome.app.repository.device;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class AllDeviceRepository extends AbstractRepository<DeviceBaseEntity> {

    public AllDeviceRepository() {
        super(DeviceBaseEntity.class, "od_");
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceBaseEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    @Transactional
    public void resetDeviceStatuses() {
        for (DeviceBaseEntity entity : this.listAll()) {
            if (entity.isResetStatusAtStartup() && (entity.getStatus() != Status.UNKNOWN ||
                    entity.getJoined() != Status.UNKNOWN || StringUtils.isNotEmpty(entity.getStatusMessage()))) {
                entity.setStatus(Status.UNKNOWN);
                entity.setJoined(Status.UNKNOWN);
                entity.setStatusMessage("");

                save(entity);
            }
        }
    }
}
