package org.homio.addon.tuya.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.DeviceListInfo;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

@Log4j2
@Getter
public class TuyaProjectService implements ServiceInstance<TuyaProjectEntity> {

    private final EntityContext entityContext;
    private final TuyaOpenAPI api;
    private long entityCode;

    @Getter
    private @NotNull TuyaProjectEntity entity;
    private ThreadContext<Void> apiConnectFuture;
    private ThreadContext<Void> refreshTokenJob;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, @NotNull TuyaProjectEntity entity) {
        this.entityContext = entityContext;
        this.entity = entity;
        this.entityCode = entity.getDeepHashCode();
        this.api = new TuyaOpenAPI();
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
        return true;
    }

    @Override
    public boolean testService() {
        return false;
    }

    public void initialize() {
        if (entity.getStatus().isOnline()) {
            return;
        }
        if (!entity.isValid()) {
            entity.setStatus(Status.ERROR, "Not valid configuration");
            return;
        }

        api.setConfig(entity);
        entity.setStatus(Status.UNKNOWN);

        stopScheduleConnectFuture();
        scheduleConnectJob();
    }

    private void scheduleConnectJob() {
        apiConnectFuture = entityContext
            .bgp().builder("tuya-login")
            .delay(Duration.ofSeconds(1))
            .interval(Duration.ofSeconds(60))
            .execute(() -> {
                if (api.login()) {
                    stopScheduleConnectFuture();
                    scheduleRefreshTokenJob();
                }
            });
    }

    private void scheduleRefreshTokenJob() {
        this.refreshTokenJob =
            entityContext.bgp().builder("tuya-refresh-token")
                         .delay(Duration.ofSeconds(api.getTuyaToken().expire - 60))
                         .execute(() -> {
                             if (!api.refreshToken()) {
                                 scheduleConnectJob();
                             }
                         });
    }

    @Override
    public void destroy() throws Exception {
        stopScheduleConnectFuture();
        stopApiRefreshFuture();
    }

    public CompletableFuture<List<DeviceListInfo>> getAllDevices(int page) {
        if (api.isConnected()) {
            return api.getDeviceList(page);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("not connected"));
    }

    private void stopScheduleConnectFuture() {
        if (EntityContextBGP.cancel(apiConnectFuture)) {
            apiConnectFuture = null;
        }
    }

    private void stopApiRefreshFuture() {
        if (EntityContextBGP.cancel(refreshTokenJob)) {
            refreshTokenJob = null;
        }
    }
}
