package org.homio.app.service.cloud;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.api.service.CloudProviderService;
import org.homio.api.service.CloudProviderService.SshCloud;
import org.homio.api.service.EntityService.WatchdogService;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.spring.ContextCreated;
import org.homio.app.ssh.SshCloudEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CloudService implements ContextCreated {

    private final EntityContext entityContext;
    @SuppressWarnings("rawtypes") private CloudProviderService cloudProvider;
    @SuppressWarnings("rawtypes") private SshCloud currentEntity;
    private ThreadContext<Void> cloudServiceThread;
    private long entityHashCode;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        currentEntity = entityContext.findAll(SshCloudEntity.class).stream().filter(SshCloudEntity::isPrimary).findAny().orElse(null);
        if (currentEntity == null) {
            currentEntity = SshCloudEntity.ensureEntityExists(entityContext);
        }
        if (currentEntity.isPrimary()) {
            entityHashCode = currentEntity.getChangesHashCode();
            start();
        }

        entityContext.event().addEntityUpdateListener(SshCloudEntity.class, "cloud-service", sshCloudEntity -> {
            if (sshCloudEntity.isPrimary()) {
                long updateChangesHashCode = sshCloudEntity.getChangesHashCode();
                if (this.currentEntity != null) {
                    if (entityHashCode == updateChangesHashCode) {
                        return;
                    }
                    this.stop(currentEntity);
                }
                entityHashCode = updateChangesHashCode;
                currentEntity = sshCloudEntity;
                start();
            } else if (currentEntity != null && sshCloudEntity.getEntityID().equals(currentEntity.getEntityID())) {
                this.stop(currentEntity);
            }
        });
        entityContext.bgp().addWatchDogService("cloud-service", new WatchdogService() {
            @Override
            public void restartService() {
                restart(currentEntity);
            }

            @Override
            public boolean isRequireRestartService() {
                return currentEntity != null
                    && currentEntity.isPrimary()
                    && currentEntity.getStatus() == Status.ERROR
                    && currentEntity.isRestartOnFailure();
            }
        });
    }

    public void restart(@NotNull SshCloud entity) {
        assertSameEntity(entity);
        stop(currentEntity);
        start();
    }

    public void stop(@NotNull SshCloud entity) {
        assertSameEntity(entity);
        if (cloudProvider != null) {
            try {
                cloudProvider.stop();
            } catch (Exception ex) {
                log.error("Error during stop cloud provider: '{}'", currentEntity, ex);
            }
        }
        if (cloudServiceThread != null) {
            log.warn("Stopping ssh cloud");
            try {
                cloudServiceThread.cancel();
            } catch (Exception ex) {
                log.error("Error during cancel cloud provider thread: '{}'", currentEntity, ex);
            }
            cloudServiceThread = null;
        }
        if (currentEntity != null) {
            currentEntity.setStatus(Status.OFFLINE);
        }
    }

    @SuppressWarnings("unchecked")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void start() {
        currentEntity.setStatus(Status.WAITING);
        cloudProvider = currentEntity.getCloudProviderService(entityContext);
        if (cloudProvider == null) {
            throw new IllegalArgumentException("SshCloudEntity: " + currentEntity.getTitle() + " returned null provider");
        }
        cloudProvider.setCurrentEntity(currentEntity);
        cloudServiceThread = entityContext.bgp().builder("cloud-" + currentEntity.getEntityID()).execute(() -> {
            try {
                log.warn("Starting cloud connection: '{}'", currentEntity);
                currentEntity.setStatus(Status.INITIALIZE);
                cloudProvider.updateNotificationBlock();
                cloudProvider.start();
                cloudProvider.updateNotificationBlock();
            } catch (Exception ex) {
                String message = CommonUtils.getErrorMessage(ex);
                currentEntity.setStatusError(ex);
                log.error("Unable to start cloud connection: '{}'. Msg: {}", currentEntity, CommonUtils.getErrorMessage(ex));
                cloudProvider.updateNotificationBlock(ex);
                entityContext.ui().sendErrorMessage("W.ERROR.UNABLE_CONNECT", FlowMap.of("MSG", message), ex);
            }
        });
    }

    private void assertSameEntity(@NotNull SshCloud entity) {
        if (currentEntity == null || !entity.getEntityID().equals(currentEntity.getEntityID())) {
            throw new IllegalStateException("SshCloudEntity: " + entity.getTitle() + " is not current active cloud ssh entity");
        }
    }
}
