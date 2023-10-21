package org.homio.addon.imou.service;

import static org.homio.addon.imou.ImouEntrypoint.IMOU_COLOR;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_ICON;

import java.time.Duration;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.addon.imou.ImouDeviceEntity;
import org.homio.addon.imou.ImouProjectEntity;
import org.homio.addon.imou.internal.cloud.ImouAPI;
import org.homio.addon.imou.internal.cloud.ImouAPI.ImouApiNotReadyException;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

@Getter
public class ImouProjectService extends ServiceInstance<ImouProjectEntity> {

    private final ImouAPI api;

    @SneakyThrows
    public ImouProjectService(@NotNull Context context, ImouProjectEntity entity) {
        super(context, entity, true);
        this.api = context.getBean(ImouAPI.class);
    }

    public void initialize() {
        ImouAPI.setProjectEntity(entity);
        entity.setStatus(Status.INITIALIZE);
        try {
            testService();
            entity.setStatusOnline();
            // fire device discovery
            context.getBean(ImouDiscoveryService.class).scan(context, null, null);
        } catch (ImouApiNotReadyException te) {
            scheduleInitialize();
        } catch (Exception ex) {
            entity.setStatusError(ex);
        } finally {
            updateNotificationBlock();
        }
    }

    public void updateNotificationBlock() {
        context.ui().notification().addBlock(entityID, "Imou", new Icon(IMOU_ICON, IMOU_COLOR), builder -> {
            builder.setStatus(entity.getStatus()).linkToEntity(entity);
            builder.setDevices(context.db().findAll(ImouDeviceEntity.class));
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
    public void destroy() {
        updateNotificationBlock();
    }

    private void scheduleInitialize() {
        context.event().runOnceOnInternetUp("imou-project-init", () -> {
            if (!entity.getStatus().isOnline()) {
                context.bgp().builder("init-imou-project-service").delay(Duration.ofSeconds(5))
                        .execute(this::initialize);
            }
        });
    }
}
