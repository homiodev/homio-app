package org.touchhome.bundle.api.repository;

import org.touchhome.bundle.api.model.DeviceBaseEntity;

public class AbstractDeviceRepository<T extends DeviceBaseEntity> extends AbstractRepository<T> {

    public AbstractDeviceRepository(Class<T> clazz, String prefix) {
        super(clazz, prefix);
    }
}
