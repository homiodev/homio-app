package org.touchhome.app.repository.device;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.repository.AbstractDeviceRepository;

import java.util.List;

import static org.touchhome.app.manager.CacheService.CACHE_ALL_DEVICES;

@Repository
public class AllDeviceRepository extends AbstractDeviceRepository {

    public AllDeviceRepository() {
        super(DeviceBaseEntity.class, "od_");
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceBaseEntity getByEntityID(String entityID) {
        return (DeviceBaseEntity) super.getByEntityID(entityID);
    }

    @Override
    @Cacheable(CACHE_ALL_DEVICES)
    @Transactional(readOnly = true)
    public List listAllWithFetchLazy() {
        return super.listAllWithFetchLazy();
    }
}
