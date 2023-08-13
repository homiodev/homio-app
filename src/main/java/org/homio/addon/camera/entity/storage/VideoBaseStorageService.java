package org.homio.addon.camera.entity.storage;

import org.homio.api.EntityContext;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.types.StorageEntity;

public abstract class VideoBaseStorageService<T extends VideoBaseStorageService> extends StorageEntity {

    public abstract void startRecord(String id, String output, String profile, DeviceBaseEntity videoEntity, EntityContext entityContext);

    public abstract void stopRecord(String id, String output, DeviceBaseEntity videoEntity);
}
