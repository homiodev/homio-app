package org.homio.addon.imou;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import java.net.URL;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.imou.internal.cloud.ImouAPI;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ImouEntrypoint implements AddonEntrypoint {

    public static final String IMOU_ICON = "fa fa-u";
    public static final String IMOU_COLOR = "#CC7D23";

    private final EntityContext entityContext;

    @Override
    public void init() {
        entityContext.setting().listenValue(ImouEntityCompactModeSetting.class, "imou-compact-mode",
            (value) -> entityContext.ui().updateItems(ImouDeviceEntity.class));
        ImouProjectEntity imouProjectEntity = ensureEntityExists(entityContext);
        entityContext.setting().listenValue(ScanImouDevicesSetting.class, "scan-imou", () ->
            imouProjectEntity.scanDevices(entityContext));

        ImouAPI.setProjectEntity(imouProjectEntity);
    }

    @SneakyThrows
    public URL getAddonImageURL() {
        return getResource("images/imou.png");
    }

    @Override
    public void destroy() {
        log.warn("Destroy imou entrypoint");
    }

    public @NotNull ImouProjectEntity ensureEntityExists(EntityContext entityContext) {
        ImouProjectEntity entity = entityContext.getEntity(ImouProjectEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new ImouProjectEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setName("Imou primary project");
            entityContext.save(entity, false);
        }
        return entity;
    }
}
