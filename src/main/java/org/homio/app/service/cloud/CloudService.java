package org.homio.app.service.cloud;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;

import javax.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.spring.ContextCreated;
import org.homio.app.ssh.SshCloudEntity;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.service.CloudProviderService;
import org.homio.bundle.api.service.CloudProviderService.SshCloud;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.util.FlowMap;
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
        if (currentEntity != null) {
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
                    this.stop();
                }
                entityHashCode = updateChangesHashCode;
                currentEntity = sshCloudEntity;
                start();
            }
        });
    }

    public void restart() {
        stop();
        start();
    }

    private void stop() {
        try {
            if (cloudProvider != null) {
                cloudProvider.stop();
            }
            if (this.cloudServiceThread != null) {
                this.cloudServiceThread.cancel();
            }
        } catch (Exception ex) {
            log.error("Unable to stop cloud connection provider: '{}'. Try restart device.", currentEntity, ex);
        }
    }

    @SuppressWarnings("unchecked")
    @RolesAllowed(ADMIN_ROLE)
    public void start() {
        cloudProvider = currentEntity.getCloudProviderService(entityContext);
        cloudProvider.setCurrentEntity(currentEntity);
        cloudServiceThread = entityContext.bgp().builder("cloud-" + cloudProvider.getName()).execute(() -> {
            try {
                log.warn("Starting cloud connection provider: '{}'", cloudProvider.getName());
                currentEntity.setStatus(Status.ONLINE);
                cloudProvider.start();
                cloudProvider.updateNotificationBlock();
            } catch (Exception ex) {
                String message = CommonUtils.getErrorMessage(ex);
                currentEntity.setStatusError(ex);
                log.error("Unable to start cloud connection provider: '{}'.", currentEntity, ex);
                cloudProvider.updateNotificationBlock(ex);
                entityContext.ui().sendErrorMessage("W.ERROR.UNABLE_CONNECT", FlowMap.of("MSG", message), null);
            }
        });
    }
}
