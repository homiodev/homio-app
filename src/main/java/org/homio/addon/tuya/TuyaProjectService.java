package org.homio.addon.tuya;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class TuyaProjectService implements ServiceInstance<TuyaProjectEntity> {

    private final EntityContext entityContext;
    @Getter
    private @NotNull TuyaProjectEntity entity;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, @NotNull TuyaProjectEntity entity) {
        this.entityContext = entityContext;
        this.entity = entity;
    }

    @Override
    public boolean entityUpdated(@NotNull TuyaProjectEntity entity) {
        this.entity = entity;
        return true;
    }

    @Override
    public boolean testService() throws Exception {
        return false;
    }

    @Override
    public void destroy() throws Exception {

    }
}
