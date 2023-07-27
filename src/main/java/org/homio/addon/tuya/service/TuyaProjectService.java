package org.homio.addon.tuya.service;

import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.api.EntityContext;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

@Log4j2
@Getter
public class TuyaProjectService extends ServiceInstance<TuyaProjectEntity> {

    private final TuyaOpenAPI api;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, @NotNull TuyaProjectEntity entity) {
        super(entityContext, entity);
        this.api = entityContext.getBean(TuyaOpenAPI.class);
        initialize();
    }

    public void initialize() {
        testServiceWithSetStatus();
    }

    @Override
    @SneakyThrows
    protected void testService() {
        entity.setStatusOnline();
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
