package org.homio.addon.tuya;

import lombok.RequiredArgsConstructor;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TuyaEntrypoint implements AddonEntrypoint {

    private final EntityContext entityContext;

    @Override
    public void init() {
        ensureEntityExists(entityContext);
    }

    @Override
    public void destroy() {
        TuyaDeviceService.destroyAll();
    }

    public static void ensureEntityExists(EntityContext entityContext) {
        TuyaProjectEntity entity = entityContext.getEntity(TuyaProjectEntity.DEFAULT_ENTITY_ID);
        if (entity == null) {
            entity = new TuyaProjectEntity()
                .setEntityID(TuyaProjectEntity.DEFAULT_ENTITY_ID)
                .setName("Tuya project");
            entity.setJsonData("dis_del", true);
            entityContext.save(entity);
        }
    }
}
