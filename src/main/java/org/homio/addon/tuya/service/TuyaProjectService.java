package org.homio.addon.tuya.service;

import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.api.EntityContext;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

public class TuyaProjectService extends ServiceInstance<TuyaProjectEntity> {

    @Getter
    private final TuyaOpenAPI api;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, @NotNull TuyaProjectEntity entity) {
        super(entityContext, entity);
        this.api = entityContext.getBean(TuyaOpenAPI.class);
        scheduleInitialize();
    }

    public void initialize() {
        entity.setStatus(Status.INITIALIZE);
        try {
            testService();
            entity.setStatusOnline();
        } catch (TuyaOpenAPI.TuyaApiNotReadyException te) {
            scheduleInitialize();
        } catch (Exception ex) {
            entity.setStatusError(ex);
        }
    }

    private void scheduleInitialize() {
        entityContext.event().runOnceOnInternetUp("tuya-project-init", () -> {
            if (!entity.getStatus().isOnline()) {
                entityContext.bgp().builder("init-tuya-project-service").delay(Duration.ofSeconds(5))
                        .execute(this::initialize);
            }
        });
    }

    @Override
    @SneakyThrows
    protected void testService() {
        if (!entity.isValid()) {
            throw new IllegalStateException("Not valid configuration");
        }
        if (!api.isConnected()) {
            api.login();
        }
    }

    @Override
    protected long getEntityHashCode(TuyaProjectEntity entity) {
        return entity.getDeepHashCode();
    }

    @Override
    public void destroy() throws Exception {
    }

    public List<TuyaDeviceDTO> getAllDevices(int page) throws Exception {
        return api.getDeviceList(page);
    }
}
