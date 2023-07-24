package org.homio.addon.tuya.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.ApiStatusCallback;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.DeviceListInfo;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

@Log4j2
@Getter
public class TuyaProjectService implements ServiceInstance<TuyaProjectEntity>, ApiStatusCallback {

    private final EntityContext entityContext;
    private final TuyaOpenAPI api;

    @Getter
    private @NotNull TuyaProjectEntity entity;
    private ThreadContext<Void> apiConnectFuture;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, @NotNull TuyaProjectEntity entity) {
        this.entityContext = entityContext;
        this.entity = entity;
        this.api = new TuyaOpenAPI(this, entityContext);
        initialize();
    }

    @Override
    public boolean entityUpdated(@NotNull TuyaProjectEntity entity) {
        this.entity = entity;
        initialize();
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

        stopApiConnectFuture();
        apiConnectFuture = entityContext.bgp().builder("tuya-login").execute(api::login);
    }

    @Override
    public void destroy() throws Exception {
        stopApiConnectFuture();
        api.disconnect();
    }

    @Override
    public void tuyaOpenApiStatus(boolean status) {
        if (!status) {
            stopApiConnectFuture();
            apiConnectFuture = entityContext.bgp().builder("tuya-login")
                                            .delay(Duration.ofSeconds(60))
                                            .execute(api::login);
            entity.setStatus(Status.OFFLINE, "COMMUNICATION_ERROR");
        } else {
            stopApiConnectFuture();
            entity.setStatus(Status.OFFLINE);
        }
    }

    public CompletableFuture<List<DeviceListInfo>> getAllDevices(int page) {
        if (api.isConnected()) {
            return api.getDeviceList(page);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("not connected"));
    }

    public CompletableFuture<DeviceSchema> getDeviceSchema(String deviceId) {
        if (api.isConnected()) {
            return api.getDeviceSchema(deviceId);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("not connected"));
    }

    private void stopApiConnectFuture() {
        if (EntityContextBGP.cancel(apiConnectFuture)) {
            apiConnectFuture = null;
        }
    }
}
