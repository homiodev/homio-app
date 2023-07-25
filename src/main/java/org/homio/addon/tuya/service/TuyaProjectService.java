package org.homio.addon.tuya.service;

import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.api.EntityContext;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;

@Log4j2
@Getter
public class TuyaProjectService implements ServiceInstance<TuyaProjectEntity> {

    private final EntityContext entityContext;
    private final TuyaOpenAPI api;
    private long entityCode;

    @Getter
    private @NotNull TuyaProjectEntity entity;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, @NotNull TuyaProjectEntity entity) {
        this.entityContext = entityContext;
        this.entity = entity;
        this.entityCode = entity.getDeepHashCode();
        this.api = entityContext.getBean(TuyaOpenAPI.class);
        initialize();
    }

    @Override
    public boolean entityUpdated(@NotNull TuyaProjectEntity newEntity) {
        long newEntityCode = newEntity.getDeepHashCode();
        boolean requireReinitialize = entityCode != newEntityCode;
        entityCode = newEntityCode;
        entity = newEntity;

        if (requireReinitialize) {
            initialize();
        }
        return false;
    }

    @Override
    public boolean testService() {
        return false;
    }

    public void initialize() {
        try {
            entity.setStatusOnline();
            if (!api.isConnected()) {
                api.login();
            }
            if (!entity.isValid()) {
                entity.setStatus(Status.ERROR, "Not valid configuration");
            }
        } catch (Exception ex) {
            entity.setStatusError(ex);
            log.error("Error during initialize tuya project: {}", CommonUtils.getErrorMessage(ex));
        }
    }

    @Override
    public void destroy() throws Exception {
    }

    public List<TuyaDeviceDTO> getAllDevices(int page) throws Exception {
        return api.getDeviceList(page);
    }
}
